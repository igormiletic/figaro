/*
 * FlatTest.scala
 * Test of flat strategies.
 *
 * Created By:      Brian Ruttenberg (bruttenberg@cra.com)
 * Creation Date:   July 1, 2015
 *
 * Copyright 2015 Avrom J. Pfeffer and Charles River Analytics, Inc.
 * See http://www.cra.com or email figaro@cra.com for information.
 *
 * See http://www.github.com/p2t2/figaro for a copy of the software license.
 */
package com.cra.figaro.test.experimental.structured.strategy

import org.scalatest.{WordSpec, Matchers}
import com.cra.figaro.language._
import com.cra.figaro.library.compound.If
import com.cra.figaro.experimental.structured.algorithm.structured.StructuredVE
import com.cra.figaro.algorithm.ValuesMaker
import com.cra.figaro.algorithm.lazyfactored.ValueSet._
import com.cra.figaro.library.atomic.discrete.Uniform
import com.cra.figaro.experimental.structured.ComponentCollection
import com.cra.figaro.experimental.structured.Problem
import com.cra.figaro.experimental.structured.solver.variableElimination
import com.cra.figaro.experimental.structured.strategy.decompose._
import com.cra.figaro.experimental.structured.Lower
import com.cra.figaro.experimental.structured.algorithm.flat.FlatVE
import com.cra.figaro.experimental.structured.strategy.solve.ConstantStrategy

class FlatTest extends WordSpec with Matchers {
  "Executing a flat strategy" when {
    
    "expanding the model" should {
      "produce the correct factors" in {
        Universe.createNew()
        val e1 = Flip(0.4)        
        val r1 = Chain(e1, (b: Boolean) => {
          if (b) Chain(Flip(0.2), (b: Boolean) => if (b) Uniform(1,2) else Uniform(3,4)) 
          else Chain(Flip(0.1), (b: Boolean) => if (b) Uniform(5,6) else Uniform(7,8))
        })        
        val cc = new ComponentCollection
        val problem = new Problem(cc, List(r1))
        val fs = DecompositionStrategy.recursiveFlattenStrategy(problem, new ConstantStrategy(variableElimination), defaultRangeSizer, Lower, false)
        fs.backwardChain(problem.components , Set())
        val factors =problem.components.flatMap(_.nonConstraintFactors) 
        factors.foreach(f => println(f.toReadableString))
        factors.size should be(16)
        FlatVE.probability(r1, 1) should equal (0.5*0.2*.4)
      }
    }

    "given a flat model with a compound Flip without evidence" should {
      "produce the correct answer" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = Apply(e2, (b: Boolean) => b)
        FlatVE.probability(e3, true) should equal (0.6)
      }
    }

    "given a flat model with evidence" should {
      "produce the correct answer" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = Apply(e2, (b: Boolean) => b)
        e3.observe(true)
        FlatVE.probability(e1, 0.3) should be (0.125 +- 0.000000001)
      }
    }

    "given a model with multiple targets and no evidence" should {
      "produce the correct probability over both targets" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = Apply(e2, (b: Boolean) => b)
        val alg = FlatVE(e2, e3)
        alg.start()
        alg.probability(e2, true) should equal (0.6)
        alg.probability(e3, true) should equal (0.6)
      }
    }

    "given a model with multiple targets with evidence" should {
      "produce the correct probability over both targets" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = Apply(e2, (b: Boolean) => b)
        e3.observe(true)
        val alg = FlatVE(e2, e1)
        alg.start()
        alg.probability(e2, true) should equal (1.0)
        alg.probability(e1, 0.3) should be (0.125 +- 0.000000001)
      }
    }

    "given a one-level nested model without evidence" should {
      "produce the correct answer" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = If(e2, Constant(true), Constant(false))
        val alg = FlatVE(e3)
        alg.start()
        alg.probability(e3, true) should equal (0.6)
      }
    }

    "given a one-level nested model with nested evidence" should {
      "produce the correct answer" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = If(e2, { val e = Flip(0.5); e.observe(true); e }, Constant(false))
        val alg = FlatVE(e3)
        alg.start()
        alg.probability(e3, true) should equal (0.6)
      }
    }

    "given a two-level nested model" should {
      "produce the correct answer" in {
        Universe.createNew()
        val e1 = Select(0.25 -> 0.3, 0.25 -> 0.5, 0.25 -> 0.7, 0.25 -> 0.9)
        val e2 = Flip(e1)
        val e3 = If(e2, If(Flip(0.9), Constant(true), Constant(false)), Constant(false))
        val alg = FlatVE(e3)
        alg.start()
        alg.probability(e3, true) should be ((0.6 * 0.9) +- 0.000000001)
      }
    }

    "expanding an element with two different arguments" should {
      "expand both the arguments" in {
        Universe.createNew()
        val e1 = Flip(0.4)
        val e2 = Flip(0.3)
        val e3 = e1 && e2
        FlatVE.probability(e3, true) should be (0.12 +- 0.000000001)
      }
    }

    "expanding an argument that is used more than once" should {
      "only expand the argument once" in {
        var count = 0
        Universe.createNew()
        val e1 = Apply(Constant(true), (b: Boolean) => { count += 1; 5 })
        val e2 = e1 === e1
        FlatVE.probability(e2, true) should equal (1.0)
        count should equal (2) // One for generating the range and one for creating the non-constraint factor. Both require applying the function.
      }
    }

    "expanding an argument that needs another argument later expanded" should {
      "create values for the ancestor argument first" in {
        Universe.createNew()
        val e1 = Flip(0.4)
        val e2 = If(e1, Constant(1), Constant(2))
        val e3 = Apply(e2, e1, (i: Int, b: Boolean) => if (b) i + 1 else i + 2)
        // e3 is 2 iff e1 is true, because then e2 is 1
        FlatVE.probability(e3, 2) should be (0.4 +- 0.000000001)
      }
    }

    "solving a problem with a reused nested subproblem" should {
      "only process the nested subproblem once" in {
        var count = 0
        val f = (p: Boolean) => {
          count += 1
          Constant(p)
        }
        val e1 = Chain(Flip(0.5), f)
        val e2 = Chain(Flip(0.4), f)
        val e3 = e1 && e2
        FlatVE.probability(e3, true) should be ((0.5 * 0.4) +- 0.000000001)
        count should equal (2) // One each for p = true and p = false, but only expanded once
      }
    }

    "given a problem with unneeded elements in the universe" should {
      "not process the unneeded elements" in {
        var count = 0
        val e1 = Apply(Constant(1), (i: Int) => { count += 1; 5 })
        val e2 = Flip(0.5)
        FlatVE.probability(e2, true) should equal (0.5)
        count should equal (0)
      }
    }
  }
}
