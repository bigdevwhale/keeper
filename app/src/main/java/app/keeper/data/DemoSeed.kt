package app.keeper.data

import android.content.Context
import app.keeper.AppContainer
import app.keeper.ui.components.Hints
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Six made-up weeks for the demo build (`-Pdemo`): a fleet of recurring thoughts, the heaviest one
 * shrinking from a galleon to a schooner, forecasts that mostly came to nothing, two ships already in
 * their bottles, and today's roadstead waiting for the watch.
 */
object DemoSeed {
    private class Plan(
        val seed: Int,
        val en: String,
        val ru: String,
        val thoughts: List<Pair<String, String>>,
        val ports: List<Port>,
        /** Days ago of the first and last sighting. */
        val from: Int,
        val to: Int,
        val count: Int,
        /** Weight at the start and at the end. */
        val w0: Float,
        val w1: Float,
        val hours: List<Int>,
    )

    private val plans = listOf(
        Plan(
            11, "The Sack", "Увольнение",
            listOf(
                "I'll be let go after the review" to "Меня уволят после ревью",
                "The boss didn't reply. That's it, then" to "Начальник не ответил — это конец",
                "Everyone can see I'm not coping" to "Все видят, что я не справляюсь",
                "If they cut anyone, it'll be me" to "Если будут сокращать, то меня",
                "One mistake and I'm out" to "Одна ошибка — и меня выгонят",
            ),
            listOf(Port.MESSAGE, Port.MEETING, Port.MEETING, Port.QUIET), 41, 0, 26, 3.8f, 2.0f, listOf(9, 10, 13, 18, 22),
        ),
        Plan(
            23, "The Heart", "Сердце",
            listOf(
                "A twinge in my chest. Something's wrong" to "Колет в груди — что-то серьёзное",
                "My heart skipped. What if it stops" to "Сердце сбилось. А вдруг остановится",
            ),
            listOf(Port.BODY, Port.NIGHT), 38, 2, 13, 3.2f, 2.2f, listOf(2, 3, 23),
        ),
        Plan(
            5, "What They Thought", "Что они подумали",
            listOf(
                "I said something stupid in the meeting" to "Я сказал глупость на встрече",
                "They laughed at me when I left" to "Они смеялись, когда я вышел",
            ),
            listOf(Port.MEETING, Port.MEMORY), 39, 22, 9, 2.6f, 1.6f, listOf(12, 19, 23),
        ),
        Plan(
            42, "Money", "Деньги",
            listOf(
                "We won't make it to payday" to "Не дотянем до зарплаты",
                "Prices are up again, we'll go under" to "Цены опять выросли, утонем",
            ),
            listOf(Port.NEWS, Port.QUIET), 30, 0, 9, 2.4f, 2.2f, listOf(8, 13, 21),
        ),
        Plan(
            77, "The Stove", "Плита",
            listOf(
                "I didn't turn the stove off" to "Я не выключил плиту",
                "The door isn't locked" to "Дверь не заперта",
            ),
            listOf(Port.QUIET), 16, 0, 7, 2.0f, 1.2f, listOf(8, 17),
        ),
        Plan(
            8, "The Exam", "Экзамен",
            listOf("I'll fail the exam and that's my whole year gone" to "Провалю экзамен — и год насмарку"),
            listOf(Port.QUIET, Port.NIGHT), 70, 40, 9, 3.4f, 2.2f, listOf(22, 23),
        ),
        Plan(
            19, "The Move", "Переезд",
            listOf("We'll never find a flat in time" to "Мы не успеем найти квартиру"),
            listOf(Port.MESSAGE, Port.NEWS), 60, 34, 6, 2.6f, 1.8f, listOf(11, 20),
        ),
    )

    suspend fun seedIfEmpty(context: Context, container: AppContainer) {
        val dao = container.db.dao()
        if (dao.ships().isNotEmpty()) return
        val ru = context.resources.configuration.locales[0].language == "ru"
        val rnd = Random(4)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(daysAgo: Int, hour: Int, minute: Int) = today.minusDays(daysAgo.toLong()).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

        val ids = plans.map { p ->
            dao.insert(Ship(name = if (ru) p.ru else p.en, seed = p.seed, createdAt = at(p.from, 8, 0)))
        }
        plans.forEachIndexed { i, p ->
            // Today's three are written by hand below.
            val last = if (p.to == 0) 1 else p.to
            repeat(p.count) { k ->
                val f = k / (p.count - 1f).coerceAtLeast(1f)
                val day = (p.from - (p.from - last) * f).toInt()
                val w = (p.w0 + (p.w1 - p.w0) * f + (rnd.nextFloat() - 0.5f) * 0.9f).toInt().coerceIn(1, 4).let { if (rnd.nextFloat() < 0.5f) it else (it + 1).coerceAtMost(4) }
                val anchored = rnd.nextFloat() < 0.38f
                val drop = rnd.nextFloat().let { d -> if (d < 0.16f) w else if (d < 0.4f) 0 else 1 + rnd.nextInt(2) }
                val (en, ruText) = p.thoughts[rnd.nextInt(p.thoughts.size)]
                val t = at(day, p.hours[rnd.nextInt(p.hours.size)], rnd.nextInt(60))
                dao.insert(
                    Sighting(
                        shipId = ids[i], at = t, text = if (ru) ruText else en, weight = w, port = p.ports[rnd.nextInt(p.ports.size)].name,
                        fate = if (anchored) Fate.WATCHED.name else Fate.PASSED.name,
                        after = if (rnd.nextFloat() < 0.85f) (w - drop).coerceAtLeast(0) else -1,
                        watchedAt = if (anchored) today.minusDays(day.toLong()).atTime(20, 10).atZone(zone).toInstant().toEpochMilli() else 0,
                    )
                )
            }
        }
        // Today: one lit and let pass, two waiting at anchor for the watch; all earlier than now, whatever the hour.
        val now = System.currentTimeMillis()
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        fun earlier(f: Float) = midnight + ((now - midnight) * f).toLong()
        dao.insert(Sighting(shipId = ids[0], at = earlier(0.3f), text = if (ru) "Начальник позвал на разговор в пятницу. Это увольнение" else "The boss wants a word on Friday. That's the sack", weight = 3, port = Port.MESSAGE.name, fate = Fate.PASSED.name, after = 2))
        dao.insert(Sighting(shipId = ids[3], at = earlier(0.6f), text = if (ru) "Не дотянем до зарплаты" else "We won't make it to payday", weight = 2, port = Port.NEWS.name, fate = Fate.ANCHORED.name))
        dao.insert(Sighting(shipId = ids[4], at = earlier(0.85f), text = if (ru) "Я не выключил плиту" else "I didn't turn the stove off", weight = 3, port = Port.QUIET.name, fate = Fate.ANCHORED.name))

        // Forecasts: mostly empty holds, one that came and was got through, and one due tonight.
        fun cargo(ship: Int, en: String, ruText: String, made: Int, due: Int, o: Outcome, coped: Boolean = false) = Cargo(
            shipId = ids[ship], text = if (ru) ruText else en, due = today.minusDays(due.toLong()).toEpochDay(), outcome = o.name, coped = coped,
            answeredAt = if (o == Outcome.PENDING) 0 else at(due.coerceAtLeast(0), 20, 20), createdAt = at(made, 20, 5),
        )
        listOf(
            cargo(0, "I'll be let go after the review", "Меня уволят после ревью", 40, 30, Outcome.EMPTY),
            cargo(0, "The boss will say I'm failing", "Начальник скажет, что я не тяну", 35, 28, Outcome.EMPTY),
            cargo(0, "I'll be the one cut in the reshuffle", "При реорганизации сократят меня", 30, 20, Outcome.EMPTY),
            cargo(0, "The review will go badly", "Ревью пройдёт плохо", 26, 19, Outcome.PARTLY, coped = true),
            cargo(0, "They'll take the project off me", "У меня заберут проект", 21, 14, Outcome.EMPTY),
            cargo(0, "A warning by the end of the month", "До конца месяца получу предупреждение", 16, 6, Outcome.EMPTY),
            cargo(0, "I'll be let go this month", "Меня уволят в этом месяце", 7, 0, Outcome.PENDING),
            cargo(0, "Friday's talk is about letting me go", "Разговор в пятницу — об увольнении", 1, -4, Outcome.PENDING),
            cargo(1, "The test will find something serious", "Анализы покажут что-то серьёзное", 36, 30, Outcome.EMPTY),
            cargo(1, "I'll end up in hospital this week", "На этой неделе попаду в больницу", 25, 18, Outcome.EMPTY),
            cargo(1, "The cardiologist will find a problem", "Кардиолог найдёт проблему", 14, 8, Outcome.EMPTY),
            cargo(2, "No one will talk to me at lunch", "Со мной не станут разговаривать за обедом", 38, 37, Outcome.EMPTY),
            cargo(2, "Someone will bring it up again", "Кто-то снова это припомнит", 30, 24, Outcome.ARRIVED, coped = true),
            cargo(3, "We'll have to borrow before payday", "Придётся занимать до зарплаты", 28, 10, Outcome.EMPTY),
            cargo(5, "I'll fail the exam", "Провалю экзамен", 66, 50, Outcome.EMPTY),
            cargo(5, "I'll have to resit", "Придётся пересдавать", 60, 45, Outcome.EMPTY),
            cargo(6, "No flat by the end of the month", "До конца месяца не будет квартиры", 58, 38, Outcome.EMPTY),
        ).forEach { dao.insert(it) }

        dao.ship(ids[0])?.let {
            dao.update(
                it.copy(
                    shoals = if (ru) "Я остаюсь без работы\nИщу новую месяца два\nТрачу подушку\nНахожу работу — может, хуже, может, лучше"
                    else "I'm out of a job\nI look for a new one for a couple of months\nI live off the savings\nI find work: maybe worse, maybe better",
                    lifeboat = if (ru) "Подушки хватит на четыре месяца. Резюме обновить — один вечер. Написать бывшей команде. Вакансии — по двадцать минут каждое утро."
                    else "The savings last four months. Updating the CV takes one evening. Write to the old team. Twenty minutes of listings every morning.",
                )
            )
        }
        dao.ship(ids[1])?.let {
            dao.update(it.copy(lifeboat = if (ru) "Если болит дольше часа или трудно дышать — звонить 103. Иначе — записаться к терапевту и не гуглить." else "If it lasts over an hour or breathing's hard, call for help. Otherwise book the GP and don't search it."))
        }
        container.settings.update {
            it.copy(onboarded = true, watchAt = 20 * 60, lastWatch = today.minusDays(1).toEpochDay(), hints = Hints.entries.map { h -> h.name }.toSet())
        }
    }
}
