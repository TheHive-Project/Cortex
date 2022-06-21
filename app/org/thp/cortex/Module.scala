package org.thp.cortex

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import net.codingwell.scalaguice.{ScalaModule, ScalaMultibinder}
import org.elastic4play.models.BaseModelDef
import org.elastic4play.services.auth.MultiAuthSrv
import org.elastic4play.services.{AuthSrv, MigrationOperations, UserSrv => EUserSrv}
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.reflections.util.ConfigurationBuilder
import org.thp.cortex.controllers.{AssetCtrl, AssetCtrlDev, AssetCtrlProd}
import org.thp.cortex.models.{AuditedModel, Migration}
import org.thp.cortex.services._
import org.thp.cortex.services.mappers.{MultiUserMapperSrv, UserMapper}
import play.api.libs.concurrent.AkkaGuiceSupport
import play.api.{Configuration, Environment, Logger, Mode}

import java.lang.reflect.Modifier
import scala.collection.JavaConverters._

class Module(environment: Environment, configuration: Configuration) extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  private lazy val logger = Logger(s"module")

  override def configure(): Unit = {
    val modelBindings        = ScalaMultibinder.newSetBinder[BaseModelDef](binder)
    val auditedModelBindings = ScalaMultibinder.newSetBinder[AuditedModel](binder)
    val reflectionClasses = new Reflections(
      new ConfigurationBuilder()
        .forPackages("org.elastic4play")
        .addClassLoaders(getClass.getClassLoader)
        .addClassLoaders(environment.getClass.getClassLoader)
        .forPackages("org.thp.cortex")
        .setExpandSuperTypes(false)
        .setScanners(Scanners.SubTypes)
    )

    reflectionClasses
      .getSubTypesOf(classOf[BaseModelDef])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers))
      .foreach { modelClass =>
        logger.info(s"Loading model $modelClass")
        modelBindings.addBinding.to(modelClass)
        if (classOf[AuditedModel].isAssignableFrom(modelClass)) {
          auditedModelBindings.addBinding.to(modelClass.asInstanceOf[Class[AuditedModel]])
        }
      }

    val authBindings = ScalaMultibinder.newSetBinder[AuthSrv](binder)
    reflectionClasses
      .getSubTypesOf(classOf[AuthSrv])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers) || c.isMemberClass)
      .filterNot(c => c == classOf[MultiAuthSrv] || c == classOf[CortexAuthSrv])
      .foreach { authSrvClass =>
        logger.info(s"Loading authentication module $authSrvClass")
        authBindings.addBinding.to(authSrvClass)
      }

    val ssoMapperBindings = ScalaMultibinder.newSetBinder[UserMapper](binder)
    reflectionClasses
      .getSubTypesOf(classOf[UserMapper])
      .asScala
      .filterNot(c => Modifier.isAbstract(c.getModifiers) || c.isMemberClass)
      .filterNot(c => c == classOf[MultiUserMapperSrv])
      .foreach(mapperCls => ssoMapperBindings.addBinding.to(mapperCls))

    if (environment.mode == Mode.Prod)
      bind[AssetCtrl].to[AssetCtrlProd]
    else
      bind[AssetCtrl].to[AssetCtrlDev]

    bind[EUserSrv].to[UserSrv]
    bind[Int].annotatedWith(Names.named("databaseVersion")).toInstance(models.modelVersion)
    bind[UserMapper].to[MultiUserMapperSrv]

    bind[AuthSrv].to[CortexAuthSrv]
    bind[MigrationOperations].to[Migration]
    bindActor[AuditActor]("audit")
  }
}
