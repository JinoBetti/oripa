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
package oripa.application.estimation;

import java.awt.Component;
import java.io.IOException;

import oripa.domain.fold.FoldedModel;
import oripa.persistent.dao.DataAccessObject;
import oripa.persistent.filetool.FileAccessSupportFilter;
import oripa.persistent.filetool.FileChooserCanceledException;

/**
 * @author OUCHI Koji
 *
 */
public class EstimationResultFileAccess {
	private final DataAccessObject<FoldedModel> dao;

	/**
	 * Constructor
	 */
	public EstimationResultFileAccess(final DataAccessObject<FoldedModel> dao) {
		this.dao = dao;
	}

	public String saveFile(final FoldedModel foldedModel, final String lastFilePath,
			final Component owner, final FileAccessSupportFilter<FoldedModel>... filters)
			throws IllegalArgumentException, IOException {
		try {
			return dao.saveUsingGUI(foldedModel, lastFilePath, owner, filters);
		} catch (FileChooserCanceledException e) {
			return lastFilePath;
		}
	}
}
