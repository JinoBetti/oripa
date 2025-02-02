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

package oripa.persistent.doc.exporter;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

import oripa.doc.Doc;
import oripa.domain.creasepattern.CreasePatternInterface;
import oripa.value.OriLine;

public class ExporterEPS implements DocExporter {

	@Override
	public boolean export(final Doc doc, final String filepath)
			throws IOException, IllegalArgumentException {
		try (var fw = new FileWriter(filepath);
				var bw = new BufferedWriter(fw);) {

			// Align the center of the model, combine scales
			bw.write("%!PS-Adobe EPSF-3\n");
			bw.write("%%BoundingBox:-200 -200 400 400\n");
			bw.write("\n");

			CreasePatternInterface creasePattern = doc.getCreasePattern();
			for (OriLine line : creasePattern) {
				bw.write("[] 0 setdash\n");
				bw.write("" + line.p0.x + " " + line.p0.y + " moveto\n");
				bw.write("" + line.p1.x + " " + line.p1.y + " lineto\n");
				bw.write("stroke\n");
			}
		}
		return true;
	}
}
