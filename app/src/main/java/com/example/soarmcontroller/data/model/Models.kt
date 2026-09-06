package com.example.soarmcontroller.data.model

import java.util.UUID

/** A saved Cartesian target for the Go To mode. Distances in millimetres, angles in degrees. */
data class GoToPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val x: Double?,
    val y: Double?,
    val z: Double?,
    val pitch: Double?,
    val roll: Double?,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One step in a taught sequence. */
sealed interface SeqStep {
    /** A captured arm pose. [label] is a human name; real joint data is attached once hardware is wired. */
    data class Pose(val label: String) : SeqStep

    /** A pause between steps, in milliseconds. */
    data class Delay(val millis: Int) : SeqStep
}

/** A saved, named sequence of steps. */
data class SequenceRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<SeqStep>,
    val createdAt: Long = System.currentTimeMillis(),
)
