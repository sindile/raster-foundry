package com.rasterfoundry.database

import com.rasterfoundry.common.datamodel.Generators.Implicits._
import com.rasterfoundry.common.datamodel.LayerAttribute

import doobie.implicits._
import cats.implicits._
import org.scalacheck.Prop.forAll
import org.scalatest._
import org.scalatest.prop.Checkers

class LayerAttributeDaoSpec
    extends FunSuite
    with Matchers
    with Checkers
    with DBTestConfig {

  test("list all layer ids") {
    xa.use(t => LayerAttributeDao.layerIds.transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  test("get max zooms for layers") {
    xa.use(
        t =>
          LayerAttributeDao
            .maxZoomsForLayers(Set.empty[String])
            .transact(t))
      .unsafeRunSync
      .length should be >= 0
  }

  // insertLayerAttribute
  test("insert a layer attribute") {
    check {
      forAll { (layerAttribute: LayerAttribute) =>
        {
          val layerId = layerAttribute.layerId
          val attributeIO = LayerAttributeDao.insertLayerAttribute(
            layerAttribute) flatMap { _ =>
            LayerAttributeDao.unsafeGetAttribute(layerId, layerAttribute.name)
          }
          xa.use(t => attributeIO.transact(t)).unsafeRunSync == layerAttribute
        }
      }
    }
  }

  // listAllAttributes
  test("list layers for an attribute name") {
    check {
      forAll { (layerAttributes: List[LayerAttribute]) =>
        {
          val attributesIO = layerAttributes.traverse(
            (attr: LayerAttribute) => {
              LayerAttributeDao.insertLayerAttribute(attr)
            }
          ) flatMap { _ =>
            LayerAttributeDao.listAllAttributes(layerAttributes.head.name)
          }

          xa.use(t => attributesIO.transact(t)).unsafeRunSync.length ==
            layerAttributes.filter(_.name == layerAttributes.head.name).length
        }
      }
    }
  }

  // layerExists
  test("check layer existence") {
    check {
      forAll { (layerAttribute: LayerAttribute) =>
        {
          val layerId = layerAttribute.layerId
          val attributesIO = LayerAttributeDao.insertLayerAttribute(
            layerAttribute) flatMap { _ =>
            LayerAttributeDao.layerExists(layerId)
          }
          xa.use(t => attributesIO.transact(t)).unsafeRunSync
        }
      }
    }
  }
}
