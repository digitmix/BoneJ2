package org.bonej.ops.ellipsoid;

import net.imagej.ImageJ;
import net.imagej.ops.special.function.BinaryFunctionOp;
import net.imagej.ops.special.function.Functions;
import net.imglib2.util.ValuePair;
import org.junit.Ignore;
import org.junit.Test;
import org.scijava.vecmath.Matrix3d;
import org.scijava.vecmath.Matrix4d;
import org.scijava.vecmath.Vector3d;
import org.scijava.vecmath.Vector4d;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *Tests for {@link FindLocalEllipsoidOp}
 *lots of expected values calculated with pen and paper or sympy.
 *calculations to be put in documentation.
 */

public class FindLocalEllipsoidOpTest {
    private static final ImageJ IMAGE_J = new ImageJ();

    @SuppressWarnings("unchecked")
    private static BinaryFunctionOp<List<Vector3d>, ValuePair<Vector3d, Vector3d>, Optional<Ellipsoid>> ellipsoidDecomposition =
            (BinaryFunctionOp) Functions.unary(IMAGE_J.op(), FindLocalEllipsoidOp.class,
                    Optional.class, List.class, ValuePair.class);

    @Ignore
    @Test
    public void testFittingEllipsoidToEquidistantCollisionPoints() {
        Vector3d vertexP = new Vector3d(-2, 0, 0);
        Vector3d normalP = new Vector3d(1, 0, 0);

        Vector3d vertexQ1 = new Vector3d(-2, 0, 0);
        Vector3d vertexQ2 = new Vector3d(0, 2, 0);
        Vector3d vertexQ3 = new Vector3d(0, -2, 0);
        Vector3d vertexQ4 = new Vector3d(0, 0, 2);
        Vector3d vertexQ5 = new Vector3d(0, 0, -2);
        Vector3d vertexTooFarAway = new Vector3d(10, -20, 4);

        List<Vector3d> arrayList = Arrays.asList(vertexP,vertexQ1, vertexQ2, vertexQ3,vertexQ4,vertexQ5,vertexTooFarAway);
        LinkedList<Vector3d> otherVertices = new LinkedList<>();
        otherVertices.addAll(arrayList);

        final Optional<Ellipsoid> ellipsoid = ellipsoidDecomposition.calculate(otherVertices, new ValuePair<>(vertexP, normalP));

        assertTrue(ellipsoid.isPresent());
        assertTrue(testPointIsOnEllipsoidSurface(vertexP, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ1, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ2, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ3, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ4, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ5, ellipsoid.get()));
        assertTrue(!testPointIsOnEllipsoidSurface(vertexTooFarAway, ellipsoid.get()));
    }



    @Ignore
    @Test
    public void testFittingEllipsoidToFiveInputPointsEasy() {
        Vector3d vertexP = new Vector3d(0, 2, 0);
        Vector3d normalP = new Vector3d(0, 1, 0);

        Vector3d vertexQ = new Vector3d(0, 4, 0);
        Vector3d vertexR = new Vector3d(3, 3, 0);
        Vector3d vertexS = new Vector3d(0, 3, 20);
        Vector3d vertexTooFarAway = new Vector3d(10, -20, 4);

        List<Vector3d> arrayList = Arrays.asList(vertexP,vertexQ, vertexR, vertexS, vertexTooFarAway);
        LinkedList<Vector3d> otherVertices = new LinkedList<>();
        otherVertices.addAll(arrayList);

        final Optional<Ellipsoid> ellipsoid = ellipsoidDecomposition.calculate(otherVertices, new ValuePair<>(vertexP, normalP));

        assertTrue(ellipsoid.isPresent());
        assertTrue(testPointIsOnEllipsoidSurface(vertexP, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexR, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexS, ellipsoid.get()));
        assertTrue(!testPointIsOnEllipsoidSurface(vertexTooFarAway, ellipsoid.get()));
    }

    @Ignore
    @Test
    public void testFittingEllipsoidToFiveInputPointsDifficult() {
        Vector3d vertexP = new Vector3d(0, 0, 0);
        Vector3d normalP = new Vector3d(0, 1, 0);

        Vector3d vertexQ = new Vector3d(1, 3, 0);
        Vector3d vertexR = new Vector3d(-4, 2, 0);
        Vector3d vertexS = new Vector3d(-2, 2, 7);
        Vector3d vertexTooFarAway = new Vector3d(10, -20, 4);

        List<Vector3d> arrayList = Arrays.asList(vertexP,vertexQ, vertexR, vertexS, vertexTooFarAway);
        LinkedList<Vector3d> otherVertices = new LinkedList<>();
        otherVertices.addAll(arrayList);

        final Optional<Ellipsoid> ellipsoid = ellipsoidDecomposition.calculate(otherVertices, new ValuePair<>(vertexP, normalP));

        assertTrue(ellipsoid.isPresent());
        assertTrue(testPointIsOnEllipsoidSurface(vertexP, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexQ, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexR, ellipsoid.get()));
        assertTrue(testPointIsOnEllipsoidSurface(vertexS, ellipsoid.get()));
        assertTrue(!testPointIsOnEllipsoidSurface(vertexTooFarAway, ellipsoid.get()));
    }


    private boolean testPointIsOnEllipsoidSurface(Vector3d point, Ellipsoid ellipsoid) {
        Vector3d xminusC = ellipsoid.getCentroid();
        xminusC.scaleAdd(-1.0, point);

        Matrix3d rotationFromAxisAligned = new Matrix3d();
        ellipsoid.getOrientation().getRotationScale(rotationFromAxisAligned);

        Matrix3d rotationToAxisAligned = new Matrix3d(rotationFromAxisAligned);
        rotationToAxisAligned.transpose();

        List<Vector3d> ellipsoidSemiAxes = ellipsoid.getSemiAxes();
        Matrix3d scale = new Matrix3d();
        scale.m00 = 1.0 / (ellipsoidSemiAxes.get(0).lengthSquared());
        scale.m11 = 1.0 / (ellipsoidSemiAxes.get(1).lengthSquared());
        scale.m22 = 1.0 / (ellipsoidSemiAxes.get(2).lengthSquared());

        Matrix3d SR = new Matrix3d();
        SR.mul(scale, rotationToAxisAligned);
        Matrix3d A = new Matrix3d();
        A.mul(rotationFromAxisAligned, SR);

        Vector3d Ax = new Vector3d(xminusC);
        A.transform(Ax);

        double shouldBeOne = xminusC.dot(Ax);

        return Math.abs(shouldBeOne - 1.0) < 1.0e-12;
    }


    @Ignore
    @Test
    public void testQ1() {
        Vector3d sphereCentre = new Vector3d(3, 4, 5);
        double radius = 7.77;

        Matrix4d q1 = FindLocalEllipsoidOp.getQ1(sphereCentre, radius);

        Matrix3d identity = new Matrix3d();
        identity.setIdentity();
        Matrix3d q1Rotation = new Matrix3d();
        q1.getRotationScale(q1Rotation);
        assertEquals(identity, q1Rotation);

        Vector4d expected = new Vector4d(-3, -4, -5, 50 - 7.77 * 7.77);

        Vector4d bottomRow = new Vector4d();
        q1.getRow(3, bottomRow);
        assertEquals(expected, bottomRow);

        Vector4d rightColumn = new Vector4d();
        q1.getColumn(3, rightColumn);
        assertEquals(expected, rightColumn);

    }

    @Ignore
    @Test
    public void testQ2() {
        Vector3d p = new Vector3d(4, 4, 1);
        Vector3d q = new Vector3d(2, 2, 1);
        Vector3d np = new Vector3d(-Math.sqrt(2) / 2.0, -Math.sqrt(2) / 2.0, 0);
        Vector3d nq = new Vector3d(Math.sqrt(2) / 2.0, Math.sqrt(2) / 2.0, 0);


        // @formatter:off
        final Matrix4d expected = new Matrix4d(
                0.5, 0.5, 0.0, -3.0,
                0.5, 0.5, 0.0, -3.0,
                0.0, 0.0, 0.0, 0.0,
                -3.0, -3.0, 0.0, 16.0
        );
        // @formatter:on

        Matrix4d q2 = FindLocalEllipsoidOp.getQ2(new VertexWithNormal(new ValuePair<>(p, np)), new VertexWithNormal(new ValuePair<>(q, nq)));

        assertTrue(q2.epsilonEquals(expected, 1.0e-12));
    }
}
