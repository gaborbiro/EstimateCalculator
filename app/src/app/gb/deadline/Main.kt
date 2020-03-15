package app.gb.deadline

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.util.*
import kotlin.math.ceil


object Main {

    private val gson = GsonBuilder().registerTypeAdapter(object : TypeToken<LocalDate?>() {}.type, LocalDateConverter()).create()

    @JvmStatic
    fun main(args: Array<String>) {
        val projectSetupFile = if (args.isNotEmpty()) args[0] else "input.json"
        val project: ProjectSetup = gson.fromJson(Files.readString(Paths.get(projectSetupFile)), ProjectSetup::class.java)
//        val project = ProjectSetup(
//                estimatedWorkHours = 39,
//                inputEstimateScenario = Scenario.BEST_CASE,
//                safetyMargin = 0.3,
//                startDate = LocalDate.of(2020, 3, 16),
//                currency = Currency.getInstance("GBP"),
//                hourlyFee = 35.0,
//                weeklyAvailableHours = 30,
//                availabilityRestrictions = AvailabilityRestrictions.builder()
//                        .bestCase().until(LocalDate.of(2020, 3, 29)).canOnlyDo(10)
//                        .worstCase().until(LocalDate.of(2020, 4, 5)).canOnlyDo(10)
//                        .build()
//        )
        getEstimate(setup = project).let {
            println("Start date:\n\t\t${project.startDate}")
            println("Estimates:")
            it[Scenario.BEST_CASE]?.let {
                println("\t\tBest case:  ${it.workHours}hr, done by ${it.deadline}")
            }
            it[Scenario.WORST_CASE]?.let {
                println("\t\tWorst case: ${it.workHours}hr, done by ${it.deadline}")
            }
            it[Scenario.REALISTIC]?.let {
                println("\t\tRealistic:  ${it.workHours}hr, done by ${it.deadline}")
                println("Fee:\n\t\t${it.formattedFee} ±${it.formattedFeeMargin}")
            }
        }
        println()
        println("Hit new line to exit...")
        Scanner(System.`in`).nextLine()
    }

    private fun getEstimate(setup: ProjectSetup): Map<Scenario, Estimate> {
        val (bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours) = when (setup.inputEstimateScenario) {
            Scenario.BEST_CASE -> {
                val bestCaseWorkHours = setup.estimatedWorkHours
                val realisticWorkHours = (bestCaseWorkHours * (1 + setup.safetyMargin)).toInt()
                val worstCaseWorkHours = (bestCaseWorkHours * (1 + 2 * setup.safetyMargin)).toInt()
                Triple(bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours)
            }
            Scenario.REALISTIC -> {
                val realisticWorkHours = setup.estimatedWorkHours
                val bestCaseWorkHours = (realisticWorkHours * (1 - setup.safetyMargin)).toInt()
                val worstCaseWorkHours = (realisticWorkHours * (1 + 2 * setup.safetyMargin)).toInt()
                Triple(bestCaseWorkHours, worstCaseWorkHours, realisticWorkHours)
            }
            else -> throw NotImplementedError("WorstCase start scenario not implemented")
        }

        return getEstimates(
                mapOf(
                        Scenario.BEST_CASE to bestCaseWorkHours,
                        Scenario.WORST_CASE to worstCaseWorkHours,
                        Scenario.REALISTIC to realisticWorkHours),
                setup,
                setup.startDate)
    }

    fun getEstimates(workHours: Map<Scenario, Int>,
                     setup: ProjectSetup,
                     startDate: LocalDate): Map<Scenario, Estimate> {
        val bestCaseWorkHours = workHours[Scenario.BEST_CASE]
                ?: throw IllegalArgumentException("Best case scenario missing from workHours")
        val worstCaseWorkHours = workHours[Scenario.WORST_CASE]
                ?: throw IllegalArgumentException("Worst case scenario missing from workHours")
        val realisticWorkHours = workHours[Scenario.REALISTIC]
                ?: throw IllegalArgumentException("Realistic scenario missing from workHours")
        val feeMargin = (worstCaseWorkHours - realisticWorkHours) * setup.hourlyFee
        val deadlines = getDeadlines(bestCaseWorkHours, worstCaseWorkHours, startDate, setup)

        val currencyFormat = NumberFormat.getCurrencyInstance(Locale.getDefault()) as DecimalFormat
        with(currencyFormat) {
            currency = setup.currency
            maximumFractionDigits = 2
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }

        return mapOf(
                Scenario.BEST_CASE to Estimate(
                        workHours = bestCaseWorkHours,
                        deadline = deadlines[Scenario.BEST_CASE],
                        fee = bestCaseWorkHours * setup.hourlyFee,
                        formattedFee = currencyFormat.format(bestCaseWorkHours * setup.hourlyFee),
                        feeMargin = feeMargin,
                        formattedFeeMargin = currencyFormat.format(feeMargin),
                        currency = setup.currency),
                Scenario.WORST_CASE to Estimate(
                        workHours = worstCaseWorkHours,
                        deadline = deadlines[Scenario.WORST_CASE],
                        fee = worstCaseWorkHours * setup.hourlyFee,
                        feeMargin = feeMargin,
                        formattedFee = currencyFormat.format(worstCaseWorkHours * setup.hourlyFee),
                        formattedFeeMargin = currencyFormat.format(feeMargin),
                        currency = setup.currency),
                Scenario.REALISTIC to Estimate(
                        workHours = realisticWorkHours,
                        deadline = deadlines[Scenario.REALISTIC],
                        fee = realisticWorkHours * setup.hourlyFee,
                        formattedFee = currencyFormat.format(realisticWorkHours * setup.hourlyFee),
                        feeMargin = feeMargin,
                        formattedFeeMargin = currencyFormat.format(feeMargin),
                        currency = setup.currency)
        )
    }

    fun getDeadlines(bestCaseWorkHours: Int,
                     worstCaseWorkHours: Int,
                     startDate: LocalDate,
                     projectSetup: ProjectSetup): Map<Scenario, LocalDate> {
        projectSetup.availabilityRestrictions?.let { restriction ->
            val (bestCaseDeadline, worstCaseDeadline) = when (restriction.type) {
                AvailabilityRestrictions.Type.START -> {
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
                AvailabilityRestrictions.Type.END -> {
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
                    Scenario.BEST_CASE to bestCaseDeadline,
                    Scenario.WORST_CASE to worstCaseDeadline,
                    Scenario.REALISTIC to realisticDeadline
            )
        } ?: run {
            val bestCaseDeadline = calculateDeadline(bestCaseWorkHours, projectSetup.weeklyAvailableHours, startDate)
            val worstCaseDeadline = calculateDeadline(worstCaseWorkHours, projectSetup.weeklyAvailableHours, startDate)
            val realisticWorkHours = ceil((bestCaseWorkHours + worstCaseWorkHours) / 2f).toInt()
            val realisticDeadline = calculateDeadline(realisticWorkHours, projectSetup.weeklyAvailableHours, startDate)
            return mapOf(
                    Scenario.BEST_CASE to bestCaseDeadline,
                    Scenario.WORST_CASE to worstCaseDeadline,
                    Scenario.REALISTIC to realisticDeadline
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
                dateIndex = restrictionStartDate
            } else {
                totalWorkedHours = 0
            }
            if (totalWorkedHours!! >= workHours) {
                return restrictionStartDate.minusDays(((totalWorkedHours!! - workHours) / baseHoursPerWeek) * 7L)
            }
            lastWeeklyHours = restrictedWeeklyHours
        }
        val remainingWorkHours = workHours - totalWorkedHours!!
        return dateIndex.plusDays(ceil(remainingWorkHours / lastWeeklyHours.toFloat() * 7).toLong() - 1)
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
                dateIndex = restrictionEndDate
            }
            if (totalWorkedHours >= workHours) {
                return restrictionEndDate
            }
        }
        val remainingWorkHours = workHours - totalWorkedHours
        return dateIndex.plusDays(ceil((remainingWorkHours / baseHoursPerWeek.toFloat()) * 7).toLong() - 1)
    }

    class ProjectSetup(
            val estimatedWorkHours: Int,
            val inputEstimateScenario: Scenario,
            val safetyMargin: Double,
            val startDate: LocalDate = LocalDate.now(),
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
}