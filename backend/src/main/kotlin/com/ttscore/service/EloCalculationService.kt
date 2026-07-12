package com.ttscore.service

import com.ttscore.model.GameResult
import kotlin.math.pow
import kotlin.math.round

object EloCalculationService {
    fun calculateGameDeltas(
        homeBaseElo: Int,
        awayBaseElo: Int,
        result: GameResult,
    ): Pair<Double, Double> {
        if (result == GameResult.NOT_PLAYED) return Pair(0.0, 0.0)

        val pToWinHome = calculateProbability(homeBaseElo, awayBaseElo)
        val pToWinAway = calculateProbability(awayBaseElo, homeBaseElo)

        return if (result == GameResult.HOME) {
            val homeDelta = roundDelta(15.0 * (1.0 - pToWinHome))
            val awayDelta = roundDelta(15.0 * (0.0 - pToWinAway))
            Pair(homeDelta, awayDelta)
        } else {
            val homeDelta = roundDelta(15.0 * (0.0 - pToWinHome))
            val awayDelta = roundDelta(15.0 * (1.0 - pToWinAway))
            Pair(homeDelta, awayDelta)
        }
    }

    /**
     * Probability that a player rated [eloA] beats one rated [eloB], on the Swiss-TT 200-point
     * scale (a 200-ELO edge ≈ 91% expected score). Exposed for the match-preview matchup odds.
     */
    fun winProbability(
        eloA: Int,
        eloB: Int,
    ): Double = calculateProbability(eloA, eloB)

    private fun calculateProbability(
        eloA: Int,
        eloB: Int,
    ): Double {
        return 1.0 / (1.0 + 10.0.pow((eloB - eloA) / 200.0))
    }

    private fun roundDelta(value: Double): Double {
        return round(value * 1000.0) / 1000.0
    }
}
