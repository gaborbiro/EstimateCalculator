package app.gb.deadline

import java.time.LocalDate

/**
 * @param bestCase Weekly work hours. Dates must be increasing in order
 * @param worstCase Weekly work hours. Dates must be increasing in order
 */
class AvailabilityRestrictions private constructor(val bestCase: Map<LocalDate, Int>,
                                                   val worstCase: Map<LocalDate, Int>,
                                                   val type: Type) {

    companion object {
        fun builder(): TypelessScenarioBuilder = TypelessScenarioBuilderImpl()
    }

    interface TypelessScenarioBuilder {
        fun bestCase(): TypeBuilder
        fun worstCase(): TypeBuilder
    }

    interface TypeBuilder {
        fun startingFrom(date: LocalDate): StartHoursBuilder
        fun until(date: LocalDate): EndHoursBuilder
    }

    interface StartHoursBuilder {
        fun canOnlyDo(hoursPerWeek: Int): StartScenarioBuilder
    }

    interface EndHoursBuilder {
        fun canOnlyDo(hoursPerWeek: Int): EndScenarioBuilder
    }

    interface StartScenarioBuilder {
        fun bestCase(): StartTypeBuilder
        fun worstCase(): StartTypeBuilder
        fun build(): AvailabilityRestrictions
    }

    interface EndScenarioBuilder {
        fun bestCase(): EndTypeBuilder
        fun worstCase(): EndTypeBuilder
        fun build(): AvailabilityRestrictions
    }

    interface StartTypeBuilder {
        fun startingFrom(date: LocalDate): StartHoursBuilder
    }

    interface EndTypeBuilder {
        fun until(date: LocalDate): EndHoursBuilder
    }


    enum class Type {
        /**
         * Start type means you are available with the base hoursPerWeek up until the first date,
         * after that the restrictions apply.
         */
        START,

        /**
         * End type means the restrictions are end-dates. After the last date you are available with the base hoursPerWeek.
         */
        END
    }

    /////// Implementation

    private class TypelessScenarioBuilderImpl : TypelessScenarioBuilder {
        override fun bestCase(): TypeBuilder = TypeBuilderImpl(Scenario.BEST_CASE)
        override fun worstCase(): TypeBuilder = TypeBuilderImpl(Scenario.WORST_CASE)
    }

    private class TypeBuilderImpl(private val scenario: Scenario) : TypeBuilder {
        override fun startingFrom(date: LocalDate): StartHoursBuilderImpl = StartHoursBuilderImpl(scenario, date)
        override fun until(date: LocalDate): EndHoursBuilderImpl = EndHoursBuilderImpl(scenario, date)
    }

    private class StartHoursBuilderImpl(private val scenario: Scenario,
                                        private val date: LocalDate,
                                        private val startScenarioBuilder: StartScenarioBuilder? = null) : StartHoursBuilder {

        override fun canOnlyDo(hoursPerWeek: Int): StartScenarioBuilder = startScenarioBuilder
                ?.also {
                    (it as StartScenarioBuilderImpl).add(scenario, date, hoursPerWeek)
                }
                ?: StartScenarioBuilderImpl(scenario, date, hoursPerWeek)
    }

    private class EndHoursBuilderImpl(private val scenario: Scenario,
                                      private val date: LocalDate,
                                      private val endScenarioBuilder: EndScenarioBuilder? = null) : EndHoursBuilder {

        override fun canOnlyDo(hoursPerWeek: Int): EndScenarioBuilder = endScenarioBuilder
                ?.also {
                    (it as EndScenarioBuilderImpl).add(scenario, date, hoursPerWeek)
                }
                ?: EndScenarioBuilderImpl(scenario, date, hoursPerWeek)
    }

    private class StartScenarioBuilderImpl(scenario: Scenario, date: LocalDate, hoursPerWeek: Int) :
            ScenarioBuilder(Type.START, scenario, date, hoursPerWeek), StartScenarioBuilder {
        override fun bestCase(): StartTypeBuilderImpl = StartTypeBuilderImpl(Scenario.BEST_CASE, this)
        override fun worstCase(): StartTypeBuilderImpl = StartTypeBuilderImpl(Scenario.WORST_CASE, this)
    }

    private class EndScenarioBuilderImpl(scenario: Scenario, date: LocalDate, hoursPerWeek: Int) :
            ScenarioBuilder(Type.END, scenario, date, hoursPerWeek), EndScenarioBuilder {
        override fun bestCase(): EndTypeBuilder = EndTypeBuilderImpl(Scenario.BEST_CASE, this)
        override fun worstCase(): EndTypeBuilder = EndTypeBuilderImpl(Scenario.WORST_CASE, this)
    }

    private class StartTypeBuilderImpl(private val scenario: Scenario, private val startScenarioBuilder: StartScenarioBuilderImpl) : StartTypeBuilder {
        override fun startingFrom(date: LocalDate): StartHoursBuilderImpl = StartHoursBuilderImpl(scenario, date, startScenarioBuilder)
    }

    private class EndTypeBuilderImpl(private val scenario: Scenario, private val endScenarioBuilder: EndScenarioBuilderImpl) : EndTypeBuilder {
        override fun until(date: LocalDate): EndHoursBuilderImpl = EndHoursBuilderImpl(scenario, date, endScenarioBuilder)
    }

    private abstract class ScenarioBuilder(private val type: Type, scenario: Scenario, date: LocalDate, hoursPerWeek: Int) {
        private val bestCaseRestrictions: MutableMap<LocalDate, Int> = mutableMapOf()
        private val worstCaseRestrictions: MutableMap<LocalDate, Int> = mutableMapOf()

        init {
            add(scenario, date, hoursPerWeek)
        }

        fun add(scenario: Scenario, date: LocalDate, hoursPerWeek: Int) {
            when (scenario) {
                Scenario.BEST_CASE -> {
                    bestCaseRestrictions[date] = hoursPerWeek
                }
                Scenario.WORST_CASE -> {
                    worstCaseRestrictions[date] = hoursPerWeek
                }
                Scenario.REALISTIC -> throw IllegalArgumentException("Cannot use Scenario.Realistic in building restrictions")
            }
        }

        fun build(): AvailabilityRestrictions {
            return AvailabilityRestrictions(bestCaseRestrictions, worstCaseRestrictions, type)
        }
    }
}