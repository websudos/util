/*
 * Copyright 2013 - 2017 Outworkers Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.outworkers.util.samplers

import scala.annotation.implicitNotFound
import scala.reflect.macros.blackbox
import com.outworkers.util.macros.AnnotationToolkit

@implicitNotFound("Could not emit trace for type")
trait Tracer[T] {
  def trace(instance: T): String
}

object Tracer {
  implicit def macroMaterialize[T]: Tracer[T] = macro TracerMacro.macroImpl[T]

  def apply[T : Tracer]: Tracer[T] = implicitly[Tracer[T]]

  def tupled(traces: Seq[String]*): String = {
    "(" + traces.mkString(", ") + ")"
  }
}

@macrocompat.bundle
class TracerMacro(val c: blackbox.Context) extends AnnotationToolkit {
  import c.universe._

  val packagePrefix = q"_root_.com.outworkers.util.samplers"
  private[this] val stringType = tq"java.lang.String"

  def macroImpl[T : WeakTypeTag]: Tree = {
    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    tpe match {
      case t if isCaseClass(t) => fieldTracer(tpe, fields(tpe))
      case t if isTuple(t) => {
        val fields = tupleFields(tpe)
        Console.println(s"Tuple ${printType(tpe)} has ${fields.size} entries.")
        val args = fields.map(acc => q"$packagePrefix.Tracer[${acc.paramType}].trace(${acc.name})")
        val code = q"""
          new $packagePrefix.Tracer[$tpe] {
            override def trace(instance: $tpe): $stringType = {
              $packagePrefix.Tracer.tupled(..$args)
            }
          }
        """
        Console.println(showCode(code))
        code
      }

      case t if tpe <:< typeOf[Option[_]] =>
        q"new $packagePrefix.Tracers.OptionTracer[$tpe]"

      case t if tpe <:< typeOf[TraversableOnce[_]] =>
        tpe.typeArgs match {
          case Nil =>
            q"new $packagePrefix.Tracers.StringTracer[$tpe]"
          case head :: Nil =>
            Console.println(s"Found collection inner type ${printType(head)}")
            q"new $packagePrefix.Tracers.TraversableTracers[$sym, ${head}]"
          case first :: second :: Nil =>
            q"new $packagePrefix.Tracers.MapLikeTracer[$sym, $first, $second]"
          case _ =>
            q"new $packagePrefix.Tracers.StringTracer[$tpe]"
      }

      case _ => q"new $packagePrefix.Tracers.StringTracer[$tpe]"
    }
  }

  def fieldTracer(tpe: Type, fields: Iterable[Accessor]): Tree = {
    val cmp = tpe.typeSymbol.name

    val appliers = fields.map { accessor =>
      q""" "  " + ${accessor.name.toString} + "= " + $packagePrefix.Tracer[${accessor.paramType}].trace(
        instance.${accessor.name}
      )"""
    }

    q"""
      new $packagePrefix.Tracer[$tpe] {
        def trace(instance: $tpe): $stringType = {
          ${cmp.toString} + "(\n" + scala.collection.immutable.List.apply(
            ..$appliers
          ).mkString("\n") + "\n)"
        }
      }
    """
  }
}
