package oripa.persistent.doc.exporter;

import java.io.IOException;

import oripa.doc.Doc;
import oripa.domain.creasepattern.CreasePatternInterface;
import oripa.persistent.entity.exporter.CreasePatternExporterSVG;

public class ExporterSVGFactory {

	private static class CreasePatternExporter implements DocExporter {

		@Override
		public boolean export(final Doc doc, final String filepath)
				throws IOException, IllegalArgumentException {
			CreasePatternInterface creasePattern = doc.getCreasePattern();

			var exporter = new CreasePatternExporterSVG();

			return exporter.export(creasePattern, filepath);
		}

	}

	public static DocExporter createCreasePatternExporter() {
		return new CreasePatternExporter();
	}
}
