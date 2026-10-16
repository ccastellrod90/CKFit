package com.ckfit.app.data.seed

import com.ckfit.app.domain.model.LoadType
import com.ckfit.app.domain.model.MeasureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeedParserTest {

    private val plan = SeedParser.parsePlan(File("src/main/assets/seed/nivel2.json").readText())
    private val catalog = SeedParser.parseCatalog(File("src/main/assets/seed/catalog.json").readText())

    @Test
    fun `plan Nivel 2 tiene 7 dias y el domingo es descanso`() {
        assertEquals("Nivel 2", plan.name)
        assertEquals((1..7).toList(), plan.days.map { it.dayOfWeek })
        val sunday = plan.days.last()
        assertTrue(sunday.isRestDay)
        assertTrue(sunday.prescriptions.isEmpty())
        assertEquals(SeedParser.REST_DAY_COLOR, sunday.colorHex)
    }

    @Test
    fun `viernes y sabado son opcionales`() {
        assertEquals(listOf(5, 6), plan.days.filter { it.isOptional }.map { it.dayOfWeek })
    }

    @Test
    fun `plancha lateral es TIME por lado de 20 s`() {
        val monday = plan.days.first()
        val p = monday.prescriptions.single { it.exerciseName == "Plancha lateral" }
        val ex = plan.exercises.single { it.name == "Plancha lateral" }
        assertEquals(MeasureType.TIME, ex.measureType)
        assertTrue(ex.perSide)
        assertEquals(20, p.durationSec)
        assertEquals(3, p.sets)
        assertNull(p.repsMin)
        assertEquals(60, p.restSec)
        assertNull(p.restMaxSec)
    }

    @Test
    fun `rest con dos valores llena restSec y restMaxSec`() {
        val prensa = plan.days.first().prescriptions.first()
        assertEquals("Prensa (leg press)", prensa.exerciseName)
        assertEquals(LoadType.STRENGTH, prensa.loadType)
        assertEquals(6, prensa.repsMin)
        assertEquals(8, prensa.repsMax)
        assertEquals(75, prensa.restSec)
        assertEquals(90, prensa.restMaxSec)
        assertTrue(prensa.notes!!.startsWith("Pies a la anchura"))
    }

    @Test
    fun `series de intensidad llevan la nota por defecto`() {
        val all = plan.days.flatMap { it.prescriptions }
        val intense = all.filter { it.isIntensitySet }
        assertEquals(4, intense.size)
        intense.forEach { assertEquals(SeedParser.DEFAULT_INTENSITY_NOTE, it.intensityNote) }
        all.filterNot { it.isIntensitySet }.forEach { assertNull(it.intensityNote) }
    }

    @Test
    fun `ejercicios repetidos en varios dias son una sola fila`() {
        val names = plan.exercises.map { it.name }
        assertEquals(names.size, names.toSet().size)
        listOf(
            "Plancha isométrica",
            "Pantorrilla máquina sentado / grada",
            "Bird dog (3 s arriba)",
            "Elevaciones laterales con mancuerna",
        ).forEach { assertEquals(1, names.count { n -> n == it }) }
        val prescriptions = plan.days.sumOf { it.prescriptions.size }
        assertEquals(35, prescriptions)
        assertEquals(31, plan.exercises.size)
    }

    @Test
    fun `cardio es TIME de 2700 s sin descanso`() {
        val cardio = plan.days.single { it.dayOfWeek == 6 }.prescriptions.single()
        assertEquals(LoadType.CARDIO, cardio.loadType)
        assertEquals(2700, cardio.durationSec)
        assertEquals(0, cardio.restSec)
    }

    @Test
    fun `catalogo adicional sin repetidos ni choques con el plan`() {
        assertTrue(catalog.size >= 40)
        val names = catalog.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertFalse(names.any { n -> plan.exercises.any { it.name == n } })
        assertTrue(catalog.filter { it.muscleGroup == "Cardio" }.all { it.measureType == MeasureType.TIME })
    }

    @Test
    fun `definiciones distintas del mismo ejercicio fallan`() {
        val json = """
            {"routine":"X","days":[
              {"dayOfWeek":1,"title":"A","exercises":[{"name":"E","group":"G","type":"REPS","load":"VOLUME","sets":3,"reps":[8,10],"rest":[60]}]},
              {"dayOfWeek":2,"title":"B","exercises":[{"name":"E","group":"G","type":"TIME","load":"CORE","sets":3,"durationSec":20,"rest":[60]}]}
            ]}
        """.trimIndent()
        assertThrows(IllegalArgumentException::class.java) { SeedParser.parsePlan(json) }
    }
}
