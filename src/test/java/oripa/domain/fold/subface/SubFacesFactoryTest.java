/**
 * ORIPA - Origami Pattern Editor
 * Copyright (C) 2013-     ORIPA OSS Project  https://github.com/oripa/oripa
 * Copyright (C) 2005-2009 Jun Mitani         http://mitani.cs.tsukuba.ac.jp/

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
package oripa.domain.fold.subface;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import oripa.domain.creasepattern.CreasePatternInterface;
import oripa.domain.fold.halfedge.OriFace;
import oripa.domain.fold.halfedge.OrigamiModel;
import oripa.domain.fold.halfedge.OrigamiModelFactory;

/**
 * @author OUCHI Koji
 *
 */
@ExtendWith(MockitoExtension.class)
class SubFacesFactoryTest {
	@InjectMocks
	private SubFacesFactory subFacesFactory;
	@Mock
	private FacesToCreasePatternConverter facesToCPConverter;
	@Mock
	private OrigamiModelFactory modelFactory;
	@Mock
	private SplitFacesToSubFacesConverter facesToSubFacesConverter;
	@Mock
	private ParentFacesCollector parentCollector;

	@Mock
	private CreasePatternInterface cp;

	@Mock
	private OrigamiModel model;

	/**
	 * Test method for
	 * {@link oripa.domain.fold.subface.SubFacesFactory#createSubFaces(java.util.List, double)}.
	 */
	@Test
	void testCreateSubFaces() {
		final double PAPER_SIZE = 400;

		var face1 = mock(OriFace.class);
		var face2 = mock(OriFace.class);
		var face3 = mock(OriFace.class);
		var inputFaces = List.of(face1, face2, face3);

		when(facesToCPConverter.convertToCreasePattern(inputFaces)).thenReturn(cp);
		when(modelFactory.buildOrigami(cp, PAPER_SIZE)).thenReturn(model);

		var splitFaces = new ArrayList<OriFace>();
		when(model.getFaces()).thenReturn(splitFaces);

		var sub1 = createSubFaceMock();
		var sub2 = createSubFaceMock();
		var sub3 = createSubFaceMock();

		var subFacesWithDuplication = List.of(sub1, sub2, sub3);
		when(facesToSubFacesConverter.convertToSubFaces(splitFaces)).thenReturn(subFacesWithDuplication);

		when(parentCollector.collect(inputFaces, sub1, PAPER_SIZE))
				.thenReturn(List.of(face1, face2));
		when(parentCollector.collect(inputFaces, sub2, PAPER_SIZE))
				.thenReturn(List.of(face2, face1));
		when(parentCollector.collect(inputFaces, sub3, PAPER_SIZE))
				.thenReturn(List.of(face1, face2, face3));

		var subFaces = subFacesFactory.createSubFaces(inputFaces, PAPER_SIZE);

		verify(facesToCPConverter).convertToCreasePattern(inputFaces);
		verify(modelFactory).buildOrigami(cp, PAPER_SIZE);
		verify(facesToSubFacesConverter).convertToSubFaces(splitFaces);
		verify(parentCollector).collect(inputFaces, sub1, PAPER_SIZE);
		verify(parentCollector).collect(inputFaces, sub2, PAPER_SIZE);
		verify(parentCollector).collect(inputFaces, sub3, PAPER_SIZE);

		assertEquals(2, subFaces.size());

		// subface should have distinct list of parent faces.
		assertTrue(subFaces.contains(sub1));
		assertTrue(subFaces.contains(sub3));
	}

	private SubFace createSubFaceMock() {
		var sub = mock(SubFace.class);
		sub.parentFaces = new ArrayList<OriFace>();

		return sub;
	}
}
