package org.thp.cortex.benchmark

import com.typesafe.config.{Config, ConfigFactory}

object Configuration {
  val conf: Config = ConfigFactory.load()

  val userLogin    = conf.getString("user.login")
  val userPassword = conf.getString("user.password")
  val userApiKey   = conf.getString("user.apiKey")

  val baseUrl = conf.getString("baseUrl")
}
