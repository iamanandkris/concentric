package io.dsentric

import java.lang.reflect.Parameter
import kotlin.reflect.KParameter
import kotlin.reflect.full.KClasses
import kotlin.reflect.jvm.ReflectJvmMapping
import kotlin.jvm.JvmClassMappingKt
import scala.jdk.CollectionConverters.*

private[dsentric] object KotlinSupport:

  final case class ParamInfo(
    name: String,
    isNullable: Boolean,
    hasDefault: Boolean
  )

  final case class Info[T](
    params: List[ParamInfo],
    javaParams: List[Parameter],
    constructFn: java.util.function.Function[java.util.Map[String, AnyRef], T]
  )

  def info[T](clazz: Class[T]): Option[Info[T]] =
    if !isKotlinClass(clazz) then None
    else
      val kClass = JvmClassMappingKt.getKotlinClass(clazz)
      val ctorOpt = Option(KClasses.getPrimaryConstructor(kClass))
      ctorOpt.flatMap { ctor =>
        Option(ReflectJvmMapping.getJavaConstructor(ctor)).map { javaCtor =>
          javaCtor.setAccessible(true)
          val valueParams = ctor.getParameters.asScala.toList.filter(_.getKind == KParameter.Kind.VALUE)
          val params = valueParams.map { p =>
            ParamInfo(
              name = p.getName,
              isNullable = p.getType.isMarkedNullable,
              hasDefault = p.isOptional
            )
          }
          val constructFn: java.util.function.Function[java.util.Map[String, AnyRef], T] =
            (fields: java.util.Map[String, AnyRef]) =>
              val args = valueParams.flatMap { p =>
                val name = p.getName
                if fields.containsKey(name) then Some(p -> fields.get(name))
                else if p.isOptional then None
                else if p.getType.isMarkedNullable then Some(p -> null)
                else None
              }.toMap
              ctor.callBy(args.asJava).asInstanceOf[T]

          Info(
            params = params,
            javaParams = javaCtor.getParameters.toList,
            constructFn = constructFn
          )
        }
      }

  def isKotlinClass(clazz: Class[?]): Boolean =
    clazz.getAnnotation(classOf[kotlin.Metadata]) != null
