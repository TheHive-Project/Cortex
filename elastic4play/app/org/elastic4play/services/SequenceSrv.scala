package org.elastic4play.services

import javax.inject.{Inject, Singleton}

import org.elastic4play.database.{DBConfiguration, DBSequence}

@Singleton
class SequenceSrv @Inject() (db: DBConfiguration) extends DBSequence(db)
