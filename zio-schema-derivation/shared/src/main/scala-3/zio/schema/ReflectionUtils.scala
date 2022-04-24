package zio.schema

import scala.annotation.tailrec
import scala.deriving._
import scala.quoted._

class ReflectionUtils[Q <: Quotes & Singleton](val q: Q) {
  given q.type = q
  import q.reflect._

  enum MirrorType {
    case Sum
    case Product
  }

  object MirrorType {
    def from(mirror: Expr[scala.deriving.Mirror]): MirrorType = 
      mirror match {
        case '{ $x: scala.deriving.Mirror.Product } => MirrorType.Product 
        case '{ $x: scala.deriving.Mirror.Sum    }  => MirrorType.Sum 
      }
  }

  

  case class Mirror(
    mirroredType: TypeRepr,
    monoType: TypeRepr,
    types: Seq[TypeRepr],
    label: String,
    labels: Seq[String],
    mirrorType: MirrorType
  )

  object Mirror {
    def apply(mirror: Expr[scala.deriving.Mirror]): Option[Mirror] = {
      val mirrorTpe = mirror.asTerm.tpe.widen
      for {
        mt   <- findMemberType(mirrorTpe, "MirroredType")
        mmt  <- findMemberType(mirrorTpe, "MirroredMonoType")
        mets <- findMemberType(mirrorTpe, "MirroredElemTypes")
        ml   <- findMemberType(mirrorTpe, "MirroredLabel")
        mels <- findMemberType(mirrorTpe, "MirroredElemLabels")
      } yield {
        val mets0 = tupleTypeElements(mets)
        val ConstantType(StringConstant(ml0)) = ml
        val mels0 = tupleTypeElements(mels).map { case ConstantType(StringConstant(l)) => l }
        Mirror(mt, mmt, mets0, ml0, mels0, MirrorType.from(mirror))
      }
    }

    def apply(tpe: TypeRepr): Option[Mirror] = {
      val MirrorType = TypeRepr.of[scala.deriving.Mirror]

      val mtpe = Refinement(MirrorType, "MirroredType", TypeBounds(tpe, tpe))
      val instance = Implicits.search(mtpe) match {
        case iss: ImplicitSearchSuccess => Some(iss.tree.asExprOf[scala.deriving.Mirror])
        case _: ImplicitSearchFailure => None
      }
      instance.flatMap(Mirror(_))
    }
  }

  def tupleTypeElements(tp: TypeRepr): List[TypeRepr] = {
    @tailrec def loop(tp: TypeRepr, acc: List[TypeRepr]): List[TypeRepr] = tp match {
      case AppliedType(pairTpe, List(hd: TypeRepr, tl: TypeRepr)) => loop(tl, hd :: acc)
      case _ => acc
    }
    loop(tp, Nil).reverse
  }

  def low(tp: TypeRepr): TypeRepr = tp match {
    case tp: TypeBounds => tp.low
    case tp => tp
  }

  def findMemberType(tp: TypeRepr, name: String): Option[TypeRepr] = tp match {
    case Refinement(_, `name`, tp) => Some(low(tp))
    case Refinement(parent, _, _) => findMemberType(parent, name)
    case AndType(left, right) => findMemberType(left, name).orElse(findMemberType(right, name))
    case _ => None
  }
}