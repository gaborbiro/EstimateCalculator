package app.gb.deadline

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.util.*
import kotlin.math.ceil


fun main() {
    val project = ProjectSetup(
            currency = Currency.getInstance("GBP"),
            hourlyFee = 35.0,
            weeklyAvailableHours = 30,
            availabilityRestrictions = AvailabilityRestrictions.builder()
                    .bestCase().until(LocalDate.of(2020, 3, 29)).canOnlyDo(10)
                    .worstCase().until(LocalDate.of(2020, 4, 5)).canOnlyDo(10)
                    .build()
    )

    val startDate = LocalDate.of(2020, 3, 16)
    getEstimate(
            estimatedWorkHours = 39,
            inputEstimateScenario = Scenario.BestCase,
            safetyMargin = 0.3,
            projectSetup = project,
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

fun getEstimate(estimatedWorkHours: Int,
                inputEstimateScenario: Scenario,
                safetyMargin: Double,
                projectSetup: ProjectSetup,
                startDate: LocalDate = LocalDate.now()): Map<Scenario, Estimate> {

    val (bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours) = when (inputEstimateScenario) {
        is Scenario.BestCase -> {
            val bestCaseWorkHours = estimatedWorkHours
            val realisticWorkHours = (bestCaseWorkHours * (1 + safetyMargin)).toInt()
            val worstCaseWorkHours = (bestCaseWorkHours * (1 + 2 * safetyMargin)).toInt()
            Triple(bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours)
        }
        is Scenario.Realistic -> {
            val realisticWorkHours = estimatedWorkHours
            val bestCaseWorkHours = (realisticWorkHours * (1 - safetyMargin)).toInt()
            val worstCaseWorkHours = (realisticWorkHours * (1 + 2 * safetyMargin)).toInt()
            Triple(bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours)
        }
        else -> throw NotImplementedError("WorstCase start scenario not implemented")
    }

    return getEstimates(
            mapOf(
                    Scenario.BestCase to bestCaseWorkHours,
                    Scenario.WorstCase to worstCaseWorkHours,
                    Scenario.Realistic to realisticWorkHours),
            projectSetup,
            startDate)
}

fun getEstimates(workHours: Map<Scenario, Int>,
                 projectSetup: ProjectSetup,
                 startDate: LocalDate): Map<Scenario, Estimate> {
    val bestCaseWorkHours = workHours[Scenario.BestCase]
            ?: throw IllegalArgumentException("Best case scenario missing from workHours")
    val worstCaseWorkHours = workHours[Scenario.WorstCase]
            ?: throw IllegalArgumentException("Worst case scenario missing from workHours")
    val realisticWorkHours = workHours[Scenario.Realistic]
            ?: throw IllegalArgumentException("Realistic scenario missing from workHours")
    val feeMargin = (worstCaseWorkHours - realisticWorkHours) * projectSetup.hourlyFee
    val deadlines = getDeadlines(bestCaseWorkHours, worstCaseWorkHours, startDate, projectSetup)

    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()) as DecimalFormat
    with(currencyFormat) {
        currency = projectSetup.currency
        maximumFractionDigits = 2
        minimumFractionDigits = 0
        roundingMode = RoundingMode.HALF_UP
    }

    return mapOf(
            Scenario.BestCase to Estimate(
                    workHours = bestCaseWorkHours,
                    deadline = deadlines[Scenario.BestCase],
                    fee = bestCaseWorkHours * projectSetup.hourlyFee,
                    formattedFee = currencyFormat.format(bestCaseWorkHours * projectSetup.hourlyFee),
                    feeMargin = feeMargin,
                    formattedFeeMargin = currencyFormat.format(feeMargin),
                    currency = projectSetup.currency),
            Scenario.WorstCase to Estimate(
                    workHours = worstCaseWorkHours,
                    deadline = deadlines[Scenario.WorstCase],
                    fee = worstCaseWorkHours * projectSetup.hourlyFee,
                    feeMargin = feeMargin,
                    formattedFee = currencyFormat.format(worstCaseWorkHours * projectSetup.hourlyFee),
                    formattedFeeMargin = currencyFormat.format(feeMargin),
                    currency = projectSetup.currency),
            Scenario.Realistic to Estimate(
                    workHours = realisticWorkHours,
                    deadline = deadlines[Scenario.Realistic],
                    fee = realisticWorkHours * projectSetup.hourlyFee,
                    formattedFee = currencyFormat.format(realisticWorkHours * projectSetup.hourlyFee),
                    feeMargin = feeMargin,
                    formattedFeeMargin = currencyFormat.format(feeMargin),
                    currency = projectSetup.currency)
    )
}

fun getDeadlines(bestCaseWorkHours: Int,
                 worstCaseWorkHours: Int,
                 startDate: LocalDate,
                 projectSetup: ProjectSetup): Map<Scenario, LocalDate> {
    projectSetup.availabilityRestrictions?.let { restriction ->
        val (bestCaseDeadline, worstCaseDeadline) = when (restriction.type) {
            AvailabilityRestrictions.Type.Start -> {
                Pair(
                        calculateDeadlineWithStartRestriction(
                                bestCaseWorkHours,
                                projectSetup.weeklyAvailableHours,
                                startDate,
                                restriction.bestCase),
                        calculateDeadlineWithStartRestriction(
                                worstCaseWorkHours,
                                projectSetup.weeklyAvailableHours,
                                startDate,
                                restriction.worstCase)
                )
            }
            AvailabilityRestrictions.Type.End -> {
                Pair(
                        calculateDeadlineWithEndRestriction(
                                bestCaseWorkHours,
                                projectSetup.weeklyAvailableHours,
                                startDate,
                                restriction.bestCase),
                        calculateDeadlineWithEndRestriction(
                                worstCaseWorkHours,
                                projectSetup.weeklyAvailableHours,
                                startDate,
                                restriction.worstCase)
                )
            }
        }
        val realisticDeadline = (bestCaseDeadline.toEpochDay() + worstCaseDeadline.toEpochDay())
                .let { ceil(it / 2f).toLong() }
                .let { LocalDate.ofEpochDay(it) }
        return mapOf(
                Scenario.BestCase to bestCaseDeadline,
                Scenario.WorstCase to worstCaseDeadline,
                Scenario.Realistic to realisticDeadline
        )
    } ?: run {
        val bestCaseDeadline = calculateDeadline(bestCaseWorkHours, projectSetup.weeklyAvailableHours, startDate)
        val worstCaseDeadline = calculateDeadline(worstCaseWorkHours, projectSetup.weeklyAvailableHours, startDate)
        val realisticWorkHours = ceil((bestCaseWorkHours + worstCaseWorkHours) / 2f).toInt()
        val realisticDeadline = calculateDeadline(realisticWorkHours, projectSetup.weeklyAvailableHours, startDate)
        return mapOf(
                Scenario.BestCase to bestCaseDeadline,
                Scenario.WorstCase to worstCaseDeadline,
                Scenario.Realistic to realisticDeadline
        )
    }
}

private fun calculateDeadline(
        workHours: Int,
        baseHoursPerWeek: Int,
        startDate: LocalDate): LocalDate {
    return startDate.plusDays(ceil(workHours / baseHoursPerWeek.toFloat() * 7).toLong() - 1)
}

private fun calculateDeadlineWithStartRestriction(
        workHours: Int,
        baseHoursPerWeek: Int,
        startDate: LocalDate,
        restrictions: Map<LocalDate, Int>): LocalDate {
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
}

private fun calculateDeadlineWithEndRestriction(
        workHours: Int,
        baseHoursPerWeek: Int,
        startDate: LocalDate,
        restrictions: Map<LocalDate, Int>): LocalDate {
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
}

sealed class Scenario {
    object BestCase : Scenario()
    object WorstCase : Scenario()
    object Realistic : Scenario()
}

class ProjectSetup(
        val currency: Currency,
        val hourlyFee: Double,
        val weeklyAvailableHours: Int,
        val availabilityRestrictions: AvailabilityRestrictions? = null)


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
         * How much you charge? (based on the specified [ProjectSetup.hourlyFee])
         */
        val fee: Double,

        val formattedFee: String,

        /**
         * Unrelated to [fee] unless this Estimate is the [Scenario.Realistic] case. If it is, the the total fee can be
         * expressed as [fee] ±[feeMargin]
         */
        val feeMargin: Double,

        val formattedFeeMargin: String,

        /**
         * Currency of [fee] and [feeMargin]
         */
        val currency: Currency
)