/**
 * ORIPA - Origami Pattern Editor
 * Copyright (C) 2005-2009 Jun Mitani http://mitani.cs.tsukuba.ac.jp/

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package oripa.domain.fold;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import javax.vecmath.Vector2d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import oripa.domain.fold.halfedge.OriEdge;
import oripa.domain.fold.halfedge.OriFace;
import oripa.domain.fold.halfedge.OriHalfedge;
import oripa.domain.fold.halfedge.OrigamiModel;
import oripa.domain.fold.origeom.OriGeomUtil;
import oripa.domain.fold.origeom.OverlapRelationValues;
import oripa.domain.fold.stackcond.StackConditionOf3Faces;
import oripa.domain.fold.stackcond.StackConditionOf4Faces;
import oripa.domain.fold.subface.SubFace;
import oripa.domain.fold.subface.SubFacesFactory;
import oripa.geom.GeomUtil;
import oripa.geom.Line;
import oripa.util.Matrices;
import oripa.value.OriLine;

public class Folder {
	private static final Logger logger = LoggerFactory.getLogger(Folder.class);

	private ArrayList<StackConditionOf4Faces> condition4s;
	private List<SubFace> subFaces;

	private final SubFacesFactory subFacesFactory;

	// helper object
	private final FolderTool folderTool = new FolderTool();

	public Folder(final SubFacesFactory subFacesFactory) {
		this.subFacesFactory = subFacesFactory;
	}

	/**
	 * Computes folded states.
	 *
	 * @param origamiModel
	 *            half-edge based data structure. It will be affected by this
	 *            method.
	 * @param fullEstimation
	 *            whether the algorithm should compute all possible folded
	 *            states or not.
	 * @return folded model whose {@link FoldedModel#getOrigamiModel()} returns
	 *         the given {@code origamiModel}.
	 */
	public FoldedModel fold(final OrigamiModel origamiModel, final boolean fullEstimation) {

		List<OriFace> sortedFaces = origamiModel.getSortedFaces();

		List<OriFace> faces = origamiModel.getFaces();
		List<OriEdge> edges = origamiModel.getEdges();

		var overlapRelationList = new OverlapRelationList();

		var foldedModel = new FoldedModel(origamiModel, overlapRelationList);

		simpleFoldWithoutZorder(faces, edges);
		folderTool.setFacesOutline(faces);
		sortedFaces.addAll(faces);

		if (!fullEstimation) {
			origamiModel.setFolded(true);
			return foldedModel;
		}

		// After folding construct the subfaces
		double paperSize = origamiModel.getPaperSize();
		subFaces = subFacesFactory.createSubFaces(faces, paperSize);
		logger.debug("subFaces.size() = " + subFaces.size());

		int[][] overlapRelation = createOverlapRelation(faces, paperSize);

		// Set overlap relations based on valley/mountain folds information
		determineOverlapRelationByLineType(faces, overlapRelation);

		holdCondition3s(faces, paperSize, overlapRelation);

		condition4s = new ArrayList<>();
		holdCondition4s(edges, overlapRelation);

		estimation(faces, overlapRelation);

		for (SubFace sub : subFaces) {
			sub.sortFaceOverlapOrder(faces, overlapRelation);
		}

		findAnswer(faces, overlapRelationList, 0, overlapRelation, true, paperSize);

		overlapRelationList.setCurrentORmatIndex(0);
		if (overlapRelationList.isEmpty()) {
			return foldedModel;
		}

//		folderTool.setFacesOutline(faces);

		origamiModel.setFolded(true);
		return foldedModel;
	}

	/**
	 * Determines overlap relations which are left uncertain after using
	 * necessary conditions.
	 *
	 * @param faces
	 *            all faces of the origami model.
	 * @param overlapRelationList
	 *            an object to store the result
	 * @param subFaceIndex
	 *            the index of subface to be updated
	 * @param orMat
	 *            overlap relation matrix
	 * @param orMatModified
	 *            whether {@code orMat} has been changed by the previous call.
	 *            {@code true} for the first call.
	 * @param paperSize
	 *            paper size
	 */
	private void findAnswer(
			final List<OriFace> faces,
			final OverlapRelationList overlapRelationList, final int subFaceIndex, final int[][] orMat,
			final boolean orMatModified, final double paperSize) {
		List<int[][]> foldableOverlapRelations = overlapRelationList.getFoldableOverlapRelations();

		if (orMatModified) {
			if (detectPenetration(faces, orMat, paperSize)) {
				return;
			}
		}

		if (subFaceIndex == subFaces.size()) {
			var ansMat = Matrices.clone(orMat);
			foldableOverlapRelations.add(ansMat);
			return;
		}

		SubFace sub = subFaces.get(subFaceIndex);

		if (sub.allFaceOrderDecided) {
			var passMat = Matrices.clone(orMat);
			findAnswer(faces, overlapRelationList, subFaceIndex + 1, passMat, false, paperSize);
			return;
		}

		for (ArrayList<OriFace> answerStack : sub.answerStacks) {
			int size = answerStack.size();
			if (!isCorrectStackOrder(answerStack, orMat)) {
				continue;
			}
			var passMat = Matrices.clone(orMat);

			// determine overlap relations according to stack
			for (int i = 0; i < size; i++) {
				int index_i = answerStack.get(i).getFaceID();
				for (int j = i + 1; j < size; j++) {
					int index_j = answerStack.get(j).getFaceID();
					passMat[index_i][index_j] = OverlapRelationValues.UPPER;
					passMat[index_j][index_i] = OverlapRelationValues.LOWER;
				}
			}

			findAnswer(faces, overlapRelationList, subFaceIndex + 1, passMat, true, paperSize);
		}
	}

	/**
	 * Detects penetration. For face_i and its neighbor face_j, face_k
	 * penetrates the sheet of paper if face_k is between face_i and face_j in
	 * the folded state and if the connection edge of face_i and face_j is on
	 * face_k.
	 *
	 * @param faces
	 *            all faces.
	 * @param orMat
	 *            overlap relation matrix.
	 * @param paperSize
	 *            paper size.
	 * @return true if there is a face which penetrates the sheet of paper.
	 */
	private boolean detectPenetration(final List<OriFace> faces, final int[][] orMat,
			final double paperSize) {
		var checked = new boolean[faces.size()][faces.size()];

		for (int i = 0; i < faces.size(); i++) {
			for (var he : faces.get(i).halfedgeIterable()) {
				var pair = he.getPair();
				if (pair == null) {
					continue;
				}

				var index_i = he.getFace().getFaceID();
				var index_j = pair.getFace().getFaceID();

				if (checked[index_i][index_j]) {
					continue;
				}

				if (orMat[index_i][index_j] != OverlapRelationValues.LOWER &&
						orMat[index_i][index_j] != OverlapRelationValues.UPPER) {
					checked[index_i][index_j] = true;
					checked[index_j][index_i] = true;
					continue;
				}

				var penetrates = IntStream.range(0, faces.size()).parallel()
						.anyMatch(k -> {
							var face_k = faces.get(k);
							var index_k = face_k.getFaceID();
							if (index_i == index_k || index_j == index_k) {
								return false;
							}
							if (!OriGeomUtil.isLineCrossFace4(face_k, he, paperSize)) {
								return false;
							}
							if (orMat[index_i][index_j] == OverlapRelationValues.LOWER &&
									orMat[index_i][index_k] == OverlapRelationValues.LOWER &&
									orMat[index_j][index_k] == OverlapRelationValues.UPPER) {
								return true;
							} else if (orMat[index_i][index_j] == OverlapRelationValues.UPPER &&
									orMat[index_i][index_k] == OverlapRelationValues.UPPER &&
									orMat[index_j][index_k] == OverlapRelationValues.LOWER) {
								return true;
							}

							return false;
						});
				if (penetrates) {
					return true;
				}

				checked[index_i][index_j] = true;
				checked[index_j][index_i] = true;
			}
		}

		return false;
	}

	/**
	 * Whether the order of faces in {@code answerStack} is correct or not
	 * according to {@code orMat}.
	 *
	 * @param answerStack
	 *            stack of faces including the same subface.
	 * @param orMat
	 *            overlap relation matrix.
	 * @return true if the order is correct.
	 */
	private boolean isCorrectStackOrder(final List<OriFace> answerStack, final int[][] orMat) {
		int size = answerStack.size();

		for (int i = 0; i < size; i++) {
			int index_i = answerStack.get(i).getFaceID();
			for (int j = i + 1; j < size; j++) {
				int index_j = answerStack.get(j).getFaceID();
				// stack_index = 0 means the top of stack (looking down
				// the folded model).
				// therefore a face with smaller stack_index i should be
				// UPPER than stack_index j.
				if (orMat[index_i][index_j] == OverlapRelationValues.LOWER) {
					return false;
				}
			}
		}

		return true;
	}

	/**
	 * Determines overlap relations by necessary conditions.
	 *
	 * @param faces
	 *            all faces.
	 * @param orMat
	 *            overlap relation matrix
	 */
	private void estimation(final List<OriFace> faces, final int[][] orMat) {
		boolean changed;
		do {
			changed = false;
			changed |= estimate_by3faces(faces, orMat);
			changed |= estimate_by3faces2(orMat);
			changed |= estimate_by4faces(orMat);
		} while (changed);
	}

	/**
	 * Creates 3-face condition and sets to subfaces: If face[i] and face[j]
	 * touching edge is covered by face[k] then OR[i][k] = OR[j][k]
	 *
	 * @param faces
	 * @param paperSize
	 * @param overlapRelation
	 */
	private void holdCondition3s(
			final List<OriFace> faces, final double paperSize, final int[][] overlapRelation) {

		for (OriFace f_i : faces) {
			for (OriHalfedge he : f_i.halfedgeIterable()) {
				var pair = he.getPair();
				if (pair == null) {
					continue;
				}

				OriFace f_j = pair.getFace();
				if (overlapRelation[f_i.getFaceID()][f_j.getFaceID()] != OverlapRelationValues.LOWER) {
					continue;
				}
				for (OriFace f_k : faces) {
					if (f_k == f_i || f_k == f_j) {
						continue;
					}
					if (!OriGeomUtil.isLineCrossFace4(f_k, he, paperSize)) {
						continue;
					}
					StackConditionOf3Faces cond = new StackConditionOf3Faces();
					cond.upper = f_i.getFaceID();
					cond.lower = f_j.getFaceID();
					cond.other = f_k.getFaceID();

					// Add condition to all subfaces of the 3 faces
					for (SubFace sub : subFaces) {
						if (sub.parentFaces.contains(f_i) && sub.parentFaces.contains(f_j)
								&& sub.parentFaces.contains(f_k)) {
							sub.condition3s.add(cond);
						}
					}

				}
			}
		}
	}

	/**
	 * Creates 4-face condition and sets to subfaces.
	 *
	 * @param parentFaces
	 * @param paperSize
	 * @param overlapRelation
	 */
	private void holdCondition4s(
			final List<OriEdge> edges, final int[][] overlapRelation) {

		int edgeNum = edges.size();
		logger.debug("edgeNum = " + edgeNum);

		for (int i = 0; i < edgeNum; i++) {
			OriEdge e0 = edges.get(i);
			var e0Left = e0.getLeft();
			var e0Right = e0.getRight();

			if (e0Left == null || e0Right == null) {
				continue;
			}

			for (int j = i + 1; j < edgeNum; j++) {
				OriEdge e1 = edges.get(j);
				var e1Left = e1.getLeft();
				var e1Right = e1.getRight();
				if (e1Left == null || e1Right == null) {
					continue;
				}

				if (!GeomUtil.isLineSegmentsOverlap(e0Left.getPosition(),
						e0Left.getNext().getPosition(),
						e1Left.getPosition(), e1Left.getNext().getPosition())) {
					continue;
				}

				var e0LeftFace = e0Left.getFace();
				var e0RightFace = e0Right.getFace();
				var e1LeftFace = e1Left.getFace();
				var e1RightFace = e1Right.getFace();

				StackConditionOf4Faces cond = new StackConditionOf4Faces();
				// Add condition to all subfaces of the 4 faces
				boolean bOverlap = false;
				for (SubFace sub : subFaces) {
					if (sub.parentFaces.contains(e0LeftFace)
							&& sub.parentFaces.contains(e0RightFace)
							&& sub.parentFaces.contains(e1LeftFace)
							&& sub.parentFaces.contains(e1RightFace)) {
						sub.condition4s.add(cond);
						bOverlap = true;
					}
				}

				var e0LeftFaceID = e0LeftFace.getFaceID();
				var e0RightFaceID = e0RightFace.getFaceID();
				var e1LeftFaceID = e1LeftFace.getFaceID();
				var e1RightFaceID = e1RightFace.getFaceID();

				if (overlapRelation[e0LeftFaceID][e0RightFaceID] == OverlapRelationValues.UPPER) {
					cond.upper1 = e0RightFaceID;
					cond.lower1 = e0LeftFaceID;
				} else {
					cond.upper1 = e0LeftFaceID;
					cond.lower1 = e0RightFaceID;
				}
				if (overlapRelation[e1LeftFaceID][e1RightFaceID] == OverlapRelationValues.UPPER) {
					cond.upper2 = e1RightFaceID;
					cond.lower2 = e1LeftFaceID;
				} else {
					cond.upper2 = e1LeftFaceID;
					cond.lower2 = e1RightFaceID;
				}

				if (bOverlap) {
					condition4s.add(cond);
				}
			}
		}
	}

	/**
	 * Sets {@code value} to {@code orMat[i][j]}. If {@code setsPairAtSameTime}
	 * is {@code true}, This method sets inversion of {@code value} to
	 * {@code orMat[j][i]}.
	 *
	 * @param orMat
	 *            overlap relation matrix
	 * @param i
	 *            row index
	 * @param j
	 *            column index
	 * @param value
	 *            a value of {@link OverlapRelationValues}
	 * @param setsPairAtSameTime
	 *            {@code true} if {@code orMat[j][i]} should be set to inversion
	 *            of {@code value} as well.
	 */
	private void setOR(final int[][] orMat, final int i, final int j, final int value,
			final boolean setsPairAtSameTime) {
		orMat[i][j] = value;
		if (!setsPairAtSameTime) {
			return;
		}

		if (value == OverlapRelationValues.LOWER) {
			orMat[j][i] = OverlapRelationValues.UPPER;
		} else {
			orMat[j][i] = OverlapRelationValues.LOWER;
		}
	}

	/**
	 *
	 * @param orMat
	 * @param i
	 * @param j
	 * @return true if LOWER and UPPER is set.
	 */
	private boolean setLowerValueIfUndefined(final int[][] orMat, final int i, final int j) {
		if (orMat[i][j] != OverlapRelationValues.UNDEFINED) {
			return false;
		}
		orMat[i][j] = OverlapRelationValues.LOWER;
		orMat[j][i] = OverlapRelationValues.UPPER;
		return true;
	}

	/**
	 * Determines overlap relation using 4-face condition.
	 *
	 * @param orMat
	 * @return
	 */
	private boolean estimate_by4faces(final int[][] orMat) {

		boolean changed = false;

		for (StackConditionOf4Faces cond : condition4s) {

			// if: lower1 > upper2, then: upper1 > upper2, upper1 > lower2,
			// lower1 > lower2
			if (orMat[cond.lower1][cond.upper2] == OverlapRelationValues.LOWER) {
				changed |= setLowerValueIfUndefined(orMat, cond.upper1, cond.upper2);
				changed |= setLowerValueIfUndefined(orMat, cond.upper1, cond.lower2);
				changed |= setLowerValueIfUndefined(orMat, cond.lower1, cond.lower2);
			}

			// if: lower2 > upper1, then: upper2 > upper1, upper2 > lower1,
			// lower2 > lower1
			if (orMat[cond.lower2][cond.upper1] == OverlapRelationValues.LOWER) {
				changed |= setLowerValueIfUndefined(orMat, cond.upper2, cond.upper1);
				changed |= setLowerValueIfUndefined(orMat, cond.upper2, cond.lower1);
				changed |= setLowerValueIfUndefined(orMat, cond.lower2, cond.lower1);
			}

			// if: upper1 > upper2 > lower1, then: upper1 > lower2, lower2 >
			// lower1
			if (orMat[cond.upper1][cond.upper2] == OverlapRelationValues.LOWER
					&& orMat[cond.upper2][cond.lower1] == OverlapRelationValues.LOWER) {
				changed |= setLowerValueIfUndefined(orMat, cond.upper1, cond.lower2);
				changed |= setLowerValueIfUndefined(orMat, cond.lower2, cond.lower1);
			}

			// if: upper1 > lower2 > lower1, then: upper1 > upper2, upper2 >
			// lower1
			if (orMat[cond.upper1][cond.lower2] == OverlapRelationValues.LOWER
					&& orMat[cond.lower2][cond.lower1] == OverlapRelationValues.LOWER) {
				changed |= setLowerValueIfUndefined(orMat, cond.upper1, cond.upper2);
				changed |= setLowerValueIfUndefined(orMat, cond.upper2, cond.lower1);
			}

			// if: upper2 > upper1 > lower2, then: upper2 > lower1, lower1 >
			// lower2
			if (orMat[cond.upper2][cond.upper1] == OverlapRelationValues.LOWER
					&& orMat[cond.upper1][cond.lower2] == OverlapRelationValues.LOWER) {
				changed |= setLowerValueIfUndefined(orMat, cond.upper2, cond.lower1);
				changed |= setLowerValueIfUndefined(orMat, cond.lower1, cond.lower2);
			}

			// if: upper2 > lower1 > lower2, then: upper2 > upper1, upper1 >
			// lower2
			if (orMat[cond.upper2][cond.lower1] == OverlapRelationValues.LOWER
					&& orMat[cond.lower1][cond.lower2] == OverlapRelationValues.LOWER) {
				changed |= setLowerValueIfUndefined(orMat, cond.upper2, cond.upper1);
				changed |= setLowerValueIfUndefined(orMat, cond.upper1, cond.lower2);
			}
		}

		return changed;
	}

	/**
	 * If the subface a>b and b>c then a>c
	 *
	 * @param orMat
	 *            overlap-relation matrix
	 * @return whether orMat is changed or not.
	 */
	private boolean estimate_by3faces2(final int[][] orMat) {
		boolean bChanged = false;

		for (SubFace sub : subFaces) {
			while (updateOverlapRelationBy3FaceStack(sub, orMat)) {
				bChanged = true;
			}
		}
		return bChanged;
	}

	/**
	 * Updates {@code orMat} by 3-face stack condition.
	 *
	 * @param sub
	 *            subface.
	 * @param orMat
	 *            overlap relation matrix.
	 * @return true if an update happens.
	 */
	private boolean updateOverlapRelationBy3FaceStack(final SubFace sub, final int[][] orMat) {

		for (int i = 0; i < sub.parentFaces.size(); i++) {
			for (int j = i + 1; j < sub.parentFaces.size(); j++) {

				// search for undetermined relations
				int index_i = sub.parentFaces.get(i).getFaceID();
				int index_j = sub.parentFaces.get(j).getFaceID();

				if (orMat[index_i][index_j] == OverlapRelationValues.NO_OVERLAP) {
					continue;
				}
				if (orMat[index_i][index_j] != OverlapRelationValues.UNDEFINED) {
					continue;
				}
				// Find the intermediary face
				for (int k = 0; k < sub.parentFaces.size(); k++) {
					if (k == i || k == j) {
						continue;
					}

					int index_k = sub.parentFaces.get(k).getFaceID();

					if (orMat[index_i][index_k] == OverlapRelationValues.UPPER
							&& orMat[index_k][index_j] == OverlapRelationValues.UPPER) {
						setOR(orMat, index_i, index_j, OverlapRelationValues.UPPER, true);
						return true;
					}
					if (orMat[index_i][index_k] == OverlapRelationValues.LOWER
							&& orMat[index_k][index_j] == OverlapRelationValues.LOWER) {
						setOR(orMat, index_i, index_j, OverlapRelationValues.LOWER, true);
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * If face[i] and face[j] touching edge is covered by face[k] then OR[i][k]
	 * = OR[j][k]
	 *
	 * @param faces
	 * @param orMat
	 * @return whether orMat is changed or not.
	 */
	private boolean estimate_by3faces(
			final List<OriFace> faces,
			final int[][] orMat) {

		boolean bChanged = false;
		for (OriFace f_i : faces) {
			int index_i = f_i.getFaceID();
			for (OriHalfedge he : f_i.halfedgeIterable()) {
				var pair = he.getPair();
				if (pair == null) {
					continue;
				}
				OriFace f_j = pair.getFace();
				int index_j = f_j.getFaceID();

				for (OriFace f_k : faces) {
					int index_k = f_k.getFaceID();
					if (f_k == f_i || f_k == f_j) {
						continue;
					}
					if (!OriGeomUtil.isLineCrossFace(f_k, he, 0.0001)) {
						continue;
					}
					if (orMat[index_i][index_k] != OverlapRelationValues.UNDEFINED
							&& orMat[index_j][index_k] == OverlapRelationValues.UNDEFINED) {
						setOR(orMat, index_j, index_k, orMat[index_i][index_k], true);
						bChanged = true;
					} else if (orMat[index_j][index_k] != OverlapRelationValues.UNDEFINED
							&& orMat[index_i][index_k] == OverlapRelationValues.UNDEFINED) {
						setOR(orMat, index_i, index_k, orMat[index_j][index_k], true);
						bChanged = true;
					}
				}
			}
		}

		return bChanged;
	}

	private void simpleFoldWithoutZorder(
			final List<OriFace> faces, final List<OriEdge> edges) {

		int id = 0;
		for (OriFace face : faces) {
//			face.faceFront = true;
//			face.movedByFold = false;
			face.setFaceID(id);
			id++;
		}

		walkFace(faces.get(0));

		for (OriEdge e : edges) {
			var sv = e.getStartVertex();
			var ev = e.getEndVertex();

			sv.getPosition().set(e.getLeft().getPositionWhileFolding());

			var right = e.getRight();
			if (right != null) {
				ev.getPosition().set(right.getPositionWhileFolding());
			}
		}
	}

	// Recursive method that flips the faces, making the folds
	private void walkFace(final OriFace face) {
		face.setMovedByFold(true);

		face.halfedgeStream().forEach(he -> {
			var pair = he.getPair();
			if (pair == null) {
				return;
			}
			var pairFace = pair.getFace();
			if (pairFace.isMovedByFold()) {
				return;
			}

			flipFace(pairFace, he);
			pairFace.setMovedByFold(true);
			walkFace(pairFace);
		});
	}

	private void transformVertex(final Vector2d vertex, final Line preLine,
			final Vector2d afterOrigin, final Vector2d afterDir) {
		double param[] = new double[1];
		double d0 = GeomUtil.distance(vertex, preLine, param);
		double d1 = param[0];

		Vector2d footV = new Vector2d(afterOrigin);
		footV.x += d1 * afterDir.x;
		footV.y += d1 * afterDir.y;

		Vector2d afterDirFromFoot = new Vector2d();
		afterDirFromFoot.x = afterDir.y;
		afterDirFromFoot.y = -afterDir.x;

		vertex.x = footV.x + d0 * afterDirFromFoot.x;
		vertex.y = footV.y + d0 * afterDirFromFoot.y;
	}

	private void flipFace(final OriFace face, final OriHalfedge baseHe) {
		var baseHePair = baseHe.getPair();
		var baseHePairNext = baseHePair.getNext();

		// (Maybe) baseHe.pair keeps the position before folding.
		Vector2d preOrigin = new Vector2d(baseHePairNext.getPositionWhileFolding());
		Vector2d afterOrigin = new Vector2d(baseHe.getPositionWhileFolding());

		// Creates the base unit vector for before the rotation
		Vector2d baseDir = new Vector2d();
		baseDir.sub(baseHePair.getPositionWhileFolding(), baseHePairNext.getPositionWhileFolding());

		// Creates the base unit vector for after the rotation
		var baseHeNext = baseHe.getNext();
		Vector2d afterDir = new Vector2d();
		afterDir.sub(baseHeNext.getPositionWhileFolding(), baseHe.getPositionWhileFolding());
		afterDir.normalize();

		Line preLine = new Line(preOrigin, baseDir);

		// move the vertices of the face to keep the face connection
		// on baseHe
		face.halfedgeStream().forEach(he -> {
			transformVertex(he.getPositionWhileFolding(), preLine, afterOrigin, afterDir);
		});

		face.precreaseStream().forEach(precrease -> {
			transformVertex(precrease.p0, preLine, afterOrigin, afterDir);
			transformVertex(precrease.p1, preLine, afterOrigin, afterDir);
		});

		// Inversion
		if (face.isFaceFront() == baseHe.getFace().isFaceFront()) {
			Vector2d ep = baseHeNext.getPositionWhileFolding();
			Vector2d sp = baseHe.getPositionWhileFolding();

			face.halfedgeStream().forEach(he -> {
				flipVertex(he.getPositionWhileFolding(), sp, ep);
			});
			face.precreaseStream().forEach(precrease -> {
				flipVertex(precrease.p0, sp, ep);
				flipVertex(precrease.p1, sp, ep);

			});
			face.invertFaceFront();
		}
	}

	/**
	 * creates the matrix overlapRelation and fills it with "no overlap" or
	 * "undefined"
	 *
	 * @param faces
	 * @param paperSize
	 * @return
	 */
	private int[][] createOverlapRelation(final List<OriFace> faces, final double paperSize) {

		int size = faces.size();
		int[][] overlapRelation = new int[size][size];

		for (int i = 0; i < size; i++) {
			overlapRelation[i][i] = OverlapRelationValues.NO_OVERLAP;
			for (int j = i + 1; j < size; j++) {
				if (OriGeomUtil.isFaceOverlap(faces.get(i), faces.get(j), paperSize * 0.00001)) {
					overlapRelation[i][j] = OverlapRelationValues.UNDEFINED;
					overlapRelation[j][i] = OverlapRelationValues.UNDEFINED;
				} else {
					overlapRelation[i][j] = OverlapRelationValues.NO_OVERLAP;
					overlapRelation[j][i] = OverlapRelationValues.NO_OVERLAP;
				}
			}
		}

		return overlapRelation;
	}

	/**
	 * Determines the overlap relations by mountain/valley.
	 *
	 * @param faces
	 * @param overlapRelation
	 */
	private void determineOverlapRelationByLineType(
			final List<OriFace> faces, final int[][] overlapRelation) {

		for (OriFace face : faces) {
			for (OriHalfedge he : face.halfedgeIterable()) {
				var pair = he.getPair();
				if (pair == null) {
					continue;
				}
				OriFace pairFace = pair.getFace();
				var faceID = face.getFaceID();
				var pairFaceID = pairFace.getFaceID();

				// If the relation is already decided, skip
				if (overlapRelation[faceID][pairFaceID] == OverlapRelationValues.UPPER
						|| overlapRelation[faceID][pairFaceID] == OverlapRelationValues.LOWER) {
					continue;
				}

				if ((face.isFaceFront() && he.getType() == OriLine.Type.MOUNTAIN.toInt())
						|| (!face.isFaceFront() && he.getType() == OriLine.Type.VALLEY.toInt())) {
					overlapRelation[faceID][pairFaceID] = OverlapRelationValues.UPPER;
					overlapRelation[pairFaceID][faceID] = OverlapRelationValues.LOWER;
				} else {
					overlapRelation[faceID][pairFaceID] = OverlapRelationValues.LOWER;
					overlapRelation[pairFaceID][faceID] = OverlapRelationValues.UPPER;
				}
			}
		}
	}

	/**
	 * Computes position of each face after fold.
	 *
	 * @param model
	 *            half-edge based data structure. It will be affected by this
	 *            method.
	 */
	public void foldWithoutLineType(
			final OrigamiModel model) {
		List<OriEdge> edges = model.getEdges();
		List<OriFace> faces = model.getFaces();

//		for (OriFace face : faces) {
//			face.faceFront = true;
//			face.movedByFold = false;
//		}

		walkFace(faces, faces.get(0), 0);

//		Collections.sort(faces, new FaceOrderComparator());
		model.getSortedFaces().clear();
		model.getSortedFaces().addAll(faces);

		for (OriEdge e : edges) {
			var sv = e.getStartVertex();
			sv.getPosition().set(e.getLeft().getPositionWhileFolding());
		}

		folderTool.setFacesOutline(faces);
	}

	/**
	 * Make the folds by flipping the faces
	 *
	 * @param faces
	 * @param face
	 * @param walkFaceCount
	 */
	private void walkFace(final List<OriFace> faces, final OriFace face, final int walkFaceCount) {
		face.setMovedByFold(true);
		if (walkFaceCount > 1000) {
			logger.error("walkFace too deep");
			return;
		}
		face.halfedgeStream().forEach(he -> {
			var pair = he.getPair();
			if (pair == null) {
				return;
			}
			var pairFace = pair.getFace();
			if (pairFace.isMovedByFold()) {
				return;
			}

			flipFace2(faces, pairFace, he);
			pairFace.setMovedByFold(true);
			walkFace(faces, pairFace, walkFaceCount + 1);
		});
	}

	private void flipFace2(final List<OriFace> faces, final OriFace face,
			final OriHalfedge baseHe) {
		flipFace(face, baseHe);
		faces.remove(face);
		faces.add(face);
	}

	private void flipVertex(final Vector2d vertex, final Vector2d sp, final Vector2d ep) {
		var v = GeomUtil.getSymmetricPoint(vertex, sp, ep);

		vertex.x = v.x;
		vertex.y = v.y;
	}
}
