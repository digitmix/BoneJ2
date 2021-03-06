/*
BSD 2-Clause License
Copyright (c) 2018, Michael Doube, Richard Domander, Alessandro Felder
All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.
* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.
THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package org.bonej.ops;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import net.imagej.ImageJ;

import org.junit.AfterClass;
import org.junit.Test;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;

/**
 * Tests for {@link SolveQuadricEq}.
 *
 * @see org.bonej.ops.ellipsoid.QuadricToEllipsoidTest for more tests related to
 *      quadric equations.
 * @author Richard Domander
 */
public class SolveQuadricEqTest {

	private static final ImageJ IMAGE_J = new ImageJ();
	private static final double alpha = Math.cos(Math.PI / 4.0);
	private static final List<Vector3d> unitSpherePoints = Stream.of(new Vector3d(
		1, 0, 0), new Vector3d(-1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, -1,
			0), new Vector3d(0, 0, 1), new Vector3d(0, 0, -1), new Vector3d(alpha,
				alpha, 0), new Vector3d(-alpha, alpha, 0), new Vector3d(alpha, -alpha,
					0), new Vector3d(-alpha, -alpha, 0), new Vector3d(0, alpha, alpha),
		new Vector3d(0, -alpha, alpha), new Vector3d(0, alpha, -alpha),
		new Vector3d(0, -alpha, -alpha), new Vector3d(alpha, 0, alpha),
		new Vector3d(alpha, 0, -alpha), new Vector3d(-alpha, 0, alpha),
		new Vector3d(-alpha, 0, -alpha)).collect(toList());
	private static final Matrix4d solution = (Matrix4d) IMAGE_J.op().run(
		SolveQuadricEq.class, unitSpherePoints);
	private static final double a = solution.getElement(0, 0);
	private static final double b = solution.getElement(1, 1);
	private static final double c = solution.getElement(2, 2);
	private static final double d = solution.getElement(0, 1);
	private static final double e = solution.getElement(0, 2);
	private static final double f = solution.getElement(1, 2);
	private static final double g = solution.getElement(0, 3);
	private static final double h = solution.getElement(1, 3);
	private static final double i = solution.getElement(2, 3);

	@Test(expected = IllegalArgumentException.class)
	public void testMatchingFailsIfTooFewPoints() {
		final List<Vector3d> tooFewPoints = Stream.generate(Vector3d::new).limit(8)
			.collect(toList());

		IMAGE_J.op().run(SolveQuadricEq.class, tooFewPoints);
	}

	@Test
	public void testMatrixElements() {
		assertEquals("The matrix element is incorrect", 1.0, a, 1e-12);
		assertEquals("The matrix element is incorrect", 1.0, b, 1e-12);
		assertEquals("The matrix element is incorrect", 1.0, c, 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, d, 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, e, 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, f, 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, g, 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, h, 1e-12);
		assertEquals("The matrix element is incorrect", 0.0, i, 1e-12);

		for (int j = 0; j < 4; j++) {
			for (int k = 0; k < 4; k++) {
				assertEquals("Matrix is not symmetric", solution.getElement(j, k),
					solution.getElement(k, j), 1e-12);
			}
		}
	}

	@Test
	public void testSolution() {
		for (final Vector3d p : unitSpherePoints) {
			final double polynomial = a * p.x * p.x + b * p.y * p.y + c * p.z * p.z +
				2 * d * p.x * p.y + 2 * e * p.x * p.z + 2 * f * p.y * p.z + 2 * g *
					p.x + 2 * h * p.y + 2 * i * p.z;
			assertEquals("The matrix does not solve the polynomial equation", 1.0,
				polynomial, 1e-12);
		}
	}

	@AfterClass
	public static void oneTimeTearDown() {
		IMAGE_J.context().dispose();
	}
}
