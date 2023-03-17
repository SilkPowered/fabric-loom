package cx.rain.silkplugin;

import com.google.common.base.Preconditions;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.ConfigContext;

import net.fabricmc.loom.configuration.DependencyInfo;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftLibraryProvider;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftSourceSets;
import net.fabricmc.loom.configuration.providers.minecraft.MinecraftVersionMeta;
import net.fabricmc.loom.util.Constants;

import net.fabricmc.loom.util.download.DownloadExecutor;
import net.fabricmc.loom.util.download.GradleDownloadProgressListener;
import net.fabricmc.loom.util.gradle.ProgressGroup;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class SpigotProvider {
	private final Project project;

	private String minecraftVersion;	// like 1.19.3
	private String spigotApiVersion;	// like R0.1
	private String spigotSnapshotVersion;	// like 1.19.3-R0.1-SNAPSHOT
	private String spigotVersion;	// passed in, like 1.19.3-R0.1-20230314.170437-5 (from codemc nms repo)

	private File workingDir;
	private File spigotBundledServerJar;
	private File spigotExtractedServerJar;
	@Nullable
	private BundleMetadata serverBundleMetadata;

	public SpigotProvider(ConfigContext configContext) {
		this.project = configContext.project();
	}

	public Project getProject() {
		return project;
	}

	public void provide() throws Exception {
		final DependencyInfo dependency = DependencyInfo.create(getProject(), Constants.Configurations.SPIGOT);

		spigotVersion = dependency.getDependency().getVersion();
		var splitVer = spigotVersion.split("-");
		if (splitVer.length != 4) {
			throw new RuntimeException("Please use the version number from CodeMC nms repo. Like 1.19.3-R0.1-20230314.170437-5.");
		}

		minecraftVersion = splitVer[0];
		spigotApiVersion = splitVer[1];
		spigotSnapshotVersion = minecraftVersion + "-" + spigotApiVersion + "-SNAPSHOT";

		initFiles();

		downloadJars(dependency.getDependency().getVersion());

//		serverBundleMetadata = BundleMetadata.fromJar(spigotBundledServerJar.toPath());

//		extractBundledServerJar();


		MinecraftSourceSets.get(project).applyDependencies(
				(configuration, name) -> project.getDependencies().add(configuration, dependency.getDepString()),
				List.of("named")
		);
	}

	protected void initFiles() {
		workingDir = new File(getExtension().getFiles().getUserCache(), "spigot/" + spigotVersion);
		workingDir.mkdirs();

		spigotBundledServerJar = file("spigot-server.jar");
		spigotExtractedServerJar = file("spigot-extracted_server.jar");
	}

	protected LoomGradleExtension getExtension() {
		return LoomGradleExtension.get(getProject());
	}

	public File getWorkingDir() {
		return workingDir;
	}

	public File file(String path) {
		return new File(getWorkingDir(), path);
	}

	private void downloadJars(String version) throws IOException {
		try (ProgressGroup progressGroup = new ProgressGroup(getProject(), "Download Spigot jars");
			 DownloadExecutor executor = new DownloadExecutor(2)) {

			var url = "https://repo.codemc.io/repository/nms/org/spigotmc/spigot/" + spigotSnapshotVersion + "/spigot-" + version + "-remapped-mojang.jar";

			getExtension().download(url)
//					.sha1(url + ".sha1")	// Todo: sha1 mismatched?
					.progress(new GradleDownloadProgressListener("Spigot official mapped server", progressGroup::createProgressLogger))
					.downloadPathAsync(spigotBundledServerJar.toPath(), executor);
		}
	}

	protected final void extractBundledServerJar() throws IOException {
		Objects.requireNonNull(getServerBundleMetadata(), "Cannot bundled mc jar from none bundled server jar");

		getLogger().info(":Extracting spigot jar from bootstrap");

		if (getServerBundleMetadata().versions().size() != 1) {
			throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(getServerBundleMetadata().versions().size()));
		}

		getServerBundleMetadata().versions().get(0).unpackEntry(spigotBundledServerJar.toPath(), spigotExtractedServerJar.toPath(), project);
	}

	public BundleMetadata getServerBundleMetadata() {
		return serverBundleMetadata;
	}


	protected Logger getLogger() {
		return getProject().getLogger();
	}
}
