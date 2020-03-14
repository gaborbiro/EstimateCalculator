package app.gb.deadline

import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.util.*
import kotlin.math.ceil


fun main() {
    val project = Project(
            currency = Currency.getInstance("GBP"),
            hourlyFee = 35,
            availability = Availability(
                    baseHoursPerWeek = 30,
                    bestCaseRestrictions = mapOf(LocalDate.of(2020, 3, 29) to 10),
                    worstCaseRestrictions = mapOf(LocalDate.of(2020, 4, 5) to 10),
                    direction = AvailabilityRestrictionDirection.End
            ))
    val startDate = LocalDate.of(2020, 3, 16)
    getEstimate(
            workHours = 39,
            safetyMarginPercentage = 30,
            inputScenario = Scenario.BestCase,
            project = project,
            startDate = startDate).let {
        println("Start date:\n\t\t$startDate")
        println("Estimates:")
        it[Scenario.BestCase]?.let {
            println("\t\tBest case:  ${it.workHours}hr, done by ${it.deadline}")
        }
        it[Scenario.WorstCase]?.let {
            println("\t\tWorst case: ${it.workHours}hr, done by ${it.deadline}")
        }
        it[Scenario.Realistic]?.let {
            println("\t\tRealistic:  ${it.workHours}hr, done by ${it.deadline}")
            println("Fee:\n\t\t${it.formattedFee} ±${it.formattedFeeMargin}")
        }
    }
}

fun getEstimate(workHours: Int,
                safetyMarginPercentage: Int,
                inputScenario: Scenario,
                project: Project,
                startDate: LocalDate? = null): Map<Scenario, Estimate> {
    val percent: Float = safetyMarginPercentage.toFloat() / 100

    val (bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours) = when (inputScenario) {
        is Scenario.BestCase -> {
            val bestCaseWorkHours = workHours
            val realisticWorkHours = (bestCaseWorkHours * (1 + percent)).toInt()
            val worstCaseWorkHours = (bestCaseWorkHours * (1 + 2 * percent)).toInt()
            Triple(bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours)
        }
        is Scenario.Realistic -> {
            val realisticWorkHours = workHours
            val bestCaseWorkHours = (realisticWorkHours * (1 - percent)).toInt()
            val worstCaseWorkHours = (realisticWorkHours * (1 + 2 * percent)).toInt()
            Triple(bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours)
        }
        else -> throw NotImplementedError("WorstCase start scenario not implemented")
    }

    return getEstimates(
            mapOf(Scenario.BestCase to bestCaseWorkHours, Scenario.WorstCase to worstCaseWorkHours, Scenario.Realistic to realisticWorkHours),
            project,
            startDate)
}

fun getEstimates(workHours: Map<Scenario, Int>,
                 project: Project,
                 startDate: LocalDate?): Map<Scenario, Estimate> {
    val bestCaseWorkHours = workHours[Scenario.BestCase]
            ?: throw IllegalArgumentException("Best case scenario missing from workHours")
    val worstCaseWorkHours = workHours[Scenario.WorstCase]
            ?: throw IllegalArgumentException("Worst case scenario missing from workHours")
    val realisticWorkHours = workHours[Scenario.Realistic]
            ?: throw IllegalArgumentException("Realistic scenario missing from workHours")
    val feeMargin = (worstCaseWorkHours - realisticWorkHours) * project.hourlyFee
    val deadlines = getDeadlines(bestCaseWorkHours, worstCaseWorkHours, startDate, project)

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.UK) as DecimalFormat
    currencyFormat.currency = project.currency

    return mapOf(
            Scenario.BestCase to Estimate(
                    workHours = bestCaseWorkHours,
                    deadline = deadlines[Scenario.BestCase],
                    fee = bestCaseWorkHours * project.hourlyFee,
                    formattedFee = currencyFormat.format(bestCaseWorkHours * project.hourlyFee),
                    feeMargin = feeMargin,
                    formattedFeeMargin = currencyFormat.format(feeMargin),
                    currency = project.currency),
            Scenario.WorstCase to Estimate(
                    workHours = worstCaseWorkHours,
                    deadline = deadlines[Scenario.WorstCase],
                    fee = worstCaseWorkHours * project.hourlyFee,
                    feeMargin = feeMargin,
                    formattedFee = currencyFormat.format(worstCaseWorkHours * project.hourlyFee),
                    formattedFeeMargin = currencyFormat.format(feeMargin),
                    currency = project.currency),
            Scenario.Realistic to Estimate(
                    workHours = realisticWorkHours,
                    deadline = deadlines[Scenario.Realistic],
                    fee = realisticWorkHours * project.hourlyFee,
                    formattedFee = currencyFormat.format(realisticWorkHours * project.hourlyFee),
                    feeMargin = feeMargin,
                    formattedFeeMargin = currencyFormat.format(feeMargin),
                    currency = project.currency)
    )
}

fun getDeadlines(bestCaseWorkHours: Int,
                 worstCaseWorkHours: Int,
                 startDate: LocalDate?,
                 project: Project): Map<Scenario, LocalDate> {
    project.availability?.let { availability ->
        val (bestCaseDeadline, worstCaseDeadline) = when (availability.direction) {
            AvailabilityRestrictionDirection.Start -> {
                Pair(
                        calculateDeadlineWithStartRestriction(
                                bestCaseWorkHours,
                                availability.baseHoursPerWeek,
                                startDate!!,
                                availability.bestCaseRestrictions),
                        calculateDeadlineWithStartRestriction(
                                worstCaseWorkHours,
                                availability.baseHoursPerWeek,
                                startDate!!,
                                availability.worstCaseRestrictions)
                )
            }
            AvailabilityRestrictionDirection.End -> {
                Pair(
                        calculateDeadlineWithEndRestriction(
                                bestCaseWorkHours,
                                availability.baseHoursPerWeek,
                                startDate!!,
                                availability.bestCaseRestrictions),
                        calculateDeadlineWithEndRestriction(
                                worstCaseWorkHours,
                                availability.baseHoursPerWeek,
                                startDate!!,
                                availability.worstCaseRestrictions)
                )
            }
        }
        val realisticDeadline = ((bestCaseDeadline.toEpochDay() + worstCaseDeadline.toEpochDay()) / 2).let {
            LocalDate.ofEpochDay(it)
        }
        return mapOf(
                Scenario.BestCase to bestCaseDeadline,
                Scenario.Realistic to realisticDeadline,
                Scenario.WorstCase to worstCaseDeadline
        )
    }
    return emptyMap()
}

private fun calculateDeadlineWithStartRestriction(
        workHours: Int,
        baseHoursPerWeek: Int,
        startDate: LocalDate,
        restrictions: Map<LocalDate, Int>): LocalDate {
    if (restrictions.isNotEmpty()) {
        var totalWorkedHours: Int? = null
        var dateIndex = startDate
        var lastWeeklyHours = baseHoursPerWeek
        restrictions.forEach { (restrictionStartDate: LocalDate, restrictedWeeklyHours: Int) ->
            if (restrictionStartDate > dateIndex) {
                val days = Period.between(dateIndex, restrictionStartDate).days - 1
                totalWorkedHours = totalWorkedHours?.let {
                    it + (days / 7f * lastWeeklyHours).toInt()
                } ?: run {
                    (days / 7f * lastWeeklyHours).toInt()
                }
            } else {
                totalWorkedHours = 0
            }
            if (totalWorkedHours!! >= workHours) {
                return restrictionStartDate.minusDays(((totalWorkedHours!! - workHours) / baseHoursPerWeek) * 7L)
            }
            dateIndex = restrictionStartDate
            lastWeeklyHours = restrictedWeeklyHours
        }
        val remainingWorkHours = workHours - totalWorkedHours!!
        return dateIndex.plusDays(ceil(remainingWorkHours / lastWeeklyHours.toFloat() * 7).toLong())
    } else {
        return startDate.plusDays(ceil(workHours / baseHoursPerWeek.toFloat()).toLong())
    }
}

private fun calculateDeadlineWithEndRestriction(
        workHours: Int,
        baseHoursPerWeek: Int,
        startDate: LocalDate,
        restrictions: Map<LocalDate, Int>): LocalDate {
    if (restrictions.isNotEmpty()) {
        var totalWorkedHours = 0f
        var dateIndex = startDate
        var days = 0L
        restrictions.forEach { (restrictionEndDate: LocalDate, restrictedWeeklyHours: Int) ->
            if (restrictionEndDate > dateIndex) {
                days += Period.between(dateIndex, restrictionEndDate).days + 1
                totalWorkedHours += (days / 7f) * restrictedWeeklyHours
            }
            if (totalWorkedHours >= workHours) {
                return restrictionEndDate
            }
            dateIndex = restrictionEndDate
        }
        val remainingWorkHours = workHours - totalWorkedHours
        return dateIndex.plusDays(ceil((remainingWorkHours / baseHoursPerWeek.toFloat()) * 7).toLong())
    } else {
        return startDate.plusDays(ceil(workHours / baseHoursPerWeek.toFloat() * 7).toLong())
    }
}

/**
 * @param bestCaseRestrictions Weekly work hours. Dates must be increasing in order
 * @param worstCaseRestrictions Weekly work hours. Dates must be increasing in order
 */
class Availability(val baseHoursPerWeek: Int,
                   val bestCaseRestrictions: Map<LocalDate, Int>,
                   val worstCaseRestrictions: Map<LocalDate, Int>,
                   val direction: AvailabilityRestrictionDirection)

sealed class AvailabilityRestrictionDirection {
    /**
     * Start direction means you are available with the base hoursPerWeek up until the first date,
     * after that the restrictions apply.
     */
    object Start : AvailabilityRestrictionDirection()

    /**
     * End direction means the restrictions are end-dates. After the last date you are available with the base hoursPerWeek.
     */
    object End : AvailabilityRestrictionDirection()
}

sealed class Scenario {
    object BestCase : Scenario()
    object WorstCase : Scenario()
    object Realistic : Scenario()
}

class Project(
        val currency: Currency,
        val hourlyFee: Int,
        val availability: Availability?)


data class Estimate(
        /**
         * In reality there is no such thing as uninterrupted work, but for the sake of argument let's assume there is.
         * How many uninterrupted hours would it take to finish the work?
         */
        val workHours: Int,

        /**
         * When will it be done?
         */
        val deadline: LocalDate?,

        /**
         * How much you charge? (based on the specified [Project.hourlyFee])
         */
        val fee: Int,

        val formattedFee: String,

        /**
         * Unrelated to [fee] unless this Estimate is the [Scenario.Realistic] case. If it is, the the total fee can be
         * expressed as [fee] ±[feeMargin]
         */
        val feeMargin: Int,

        val formattedFeeMargin: String,

        /**
         * Currency of [fee] and [feeMargin]
         */
        val currency: Currency
)