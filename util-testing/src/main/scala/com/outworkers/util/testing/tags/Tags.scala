package com.outworkers.util.testing.tags

import scala.reflect.macros.whitebox

@macrocompat.bundle
class Tags(val c: whitebox.Context) {

  import c.universe._

  type Tagged[A, T] = { type Data = A; type Tag = T }

  private[this] val pkg = q"com.outworkers.util.testing.tags"

  def tagMacro[A: c.WeakTypeTag, T: c.WeakTypeTag]: c.Expr[Tagged[A, T]] = {
    import c.universe._
    val AT = weakTypeOf[Tagged[A, T]]
    val a = c.prefix.tree match {
      case Apply(_, List(x)) => x
      case t => c.abort(c.enclosingPosition, s"Cannot extract .tag target (tree = $t)")
    }
    c.Expr[Tagged[A, T]](q"$a.asInstanceOf[$AT]")
  }

  def untagMacro[A: c.WeakTypeTag, T: c.WeakTypeTag]: c.Expr[A] = {
    import c.universe._
    val A = weakTypeOf[A]
    val at = c.prefix.tree match {
      case Apply(_, List(x)) => x
      case t => c.abort(c.enclosingPosition, s"Cannot extract .untag target (tree = $t)")
    }
    c.Expr[A](q"$at.asInstanceOf[$A]")
  }

  def wrapMacro[A: c.WeakTypeTag, T: c.WeakTypeTag](a: c.Expr[A]): c.Expr[Tagged[A, T]] = {
    import c.universe._
    val A = weakTypeOf[A]
    val T = weakTypeOf[T]
    val AT = weakTypeOf[Tagged[A, T]]
    c.Expr[Tagged[A, T]](q"$a.asInstanceOf[$pkg.Tags.Tagged[$A, $T]]")
  }

  def unwrapMacro[A: c.WeakTypeTag, T](at: c.Expr[Tagged[A, T]]): c.Expr[A] = {
    import c.universe._
    val A = weakTypeOf[A]
    c.Expr[A](q"$at.asInstanceOf[$A]")
  }

  def wrapfMacro[F[_], A: c.WeakTypeTag, T: c.WeakTypeTag](fa: c.Expr[F[A]])(implicit F: c.WeakTypeTag[F[_]]): c.Expr[F[Tagged[A, T]]] = {
    import c.universe._
    val AT = appliedType(typeOf[Tagged[_, _]], List(weakTypeOf[A], weakTypeOf[T]))
    val FAT = appliedType(F.tpe.typeConstructor, AT :: Nil)
    val t = q"$fa.asInstanceOf[$FAT]"
    c.Expr[F[Tagged[A, T]]](t)
  }

  def unwrapfMacro[F[_], A: c.WeakTypeTag, T](fat: c.Expr[F[Tagged[A, T]]])(implicit F: c.WeakTypeTag[F[_]]): c.Expr[F[A]] = {
    import c.universe._
    val FA = appliedType(F.tpe.typeConstructor, weakTypeOf[A] :: Nil)
    c.Expr[F[A]](q"$fat.asInstanceOf[$FA]")
  }
}