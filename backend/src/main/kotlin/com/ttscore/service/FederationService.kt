package com.ttscore.service

import com.ttscore.database.Federations
import com.ttscore.database.dbQuery
import com.ttscore.model.FederationResponse

object FederationService {
    suspend fun getAll(): List<FederationResponse> =
        dbQuery {
            Federations.select(Federations.id, Federations.name)
                .map { FederationResponse(id = it[Federations.id].toString(), name = it[Federations.name]) }
        }
}
