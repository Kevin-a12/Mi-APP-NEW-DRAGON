package mx.newdragon.pos

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Business dates always use Sinaloa, independently of the tablet's settings. */
object RestaurantTime {
    val zone: ZoneId = ZoneId.of("America/Mazatlan")
    const val label = "Zona horaria: Los Mochis, Sin."
    private val timestamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX")).withZone(zone)
    fun date(millis: Long): String = timestamp.format(Instant.ofEpochMilli(millis))
    fun today(clock: Clock = Clock.systemUTC()): LocalDate = LocalDate.now(clock.withZone(zone))
    fun labelFor(value: ZoneId): String = if (value == zone) label else "Zona horaria: " + value.id
}
