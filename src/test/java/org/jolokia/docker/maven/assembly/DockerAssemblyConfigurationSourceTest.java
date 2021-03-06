package org.jolokia.docker.maven.assembly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.jolokia.docker.maven.config.AssemblyConfiguration;
import org.jolokia.docker.maven.util.EnvUtil;
import org.jolokia.docker.maven.util.MojoParameters;
import org.junit.Before;
import org.junit.Test;

public class DockerAssemblyConfigurationSourceTest {

    private AssemblyConfiguration assemblyConfig;

    @Before
    public void setup() {
        // set 'ignorePermissions' to something other then default
        this.assemblyConfig = new AssemblyConfiguration.Builder()
                .descriptor("assembly.xml")
                .descriptorRef("project")
                .ignorePermissions(false)
                .build();
    }

    @Test
    public void testCreateSourceAbsolute() {
        testCreateSource(buildParameters(".", "/src/docker".replace("/", File.separator), "/output/docker".replace("/", File.separator)));
    }

    @Test
    public void testCreateSourceRelative() {
        testCreateSource(buildParameters(".","src/docker".replace("/", File.separator), "output/docker".replace("/", File.separator)));
    }

    @Test
    public void testOutputDirHasImage() {
        String image = "image";
        MojoParameters params = buildParameters(".","src/docker", "output/docker");
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(params,
                                                                                         new BuildDirs(params,image),assemblyConfig);
        
        assertTrue(containsDir(image, source.getOutputDirectory()));
        assertTrue(containsDir(image, source.getWorkingDirectory()));
        assertTrue(containsDir(image, source.getTemporaryRootDirectory()));
    }
    
    private MojoParameters buildParameters(String projectDir, String sourceDir, String outputDir) {
        MavenProject mavenProject = new MavenProject();
        mavenProject.setFile(new File(projectDir));
        return new MojoParameters(null, mavenProject, null, null, null, sourceDir, outputDir);
    }

    @Test
    public void testEmptyAssemblyConfig() {
        DockerAssemblyConfigurationSource source = new DockerAssemblyConfigurationSource(
               new MojoParameters(null, null, null, null, null, "/src/docker", "/output/docker"),
               null,null
        );
        assertEquals(0,source.getDescriptors().length);
    }

    private void testCreateSource(MojoParameters params) {
        DockerAssemblyConfigurationSource source =
                new DockerAssemblyConfigurationSource(params, new BuildDirs(params,"image"),assemblyConfig);

        String[] descriptors = source.getDescriptors();
        String[] descriptorRefs = source.getDescriptorReferences();

        assertEquals("count of descriptors", 1, descriptors.length);
        assertEquals("directory of assembly", EnvUtil.prepareAbsoluteSourceDirPath(params, "assembly.xml").getAbsolutePath(), descriptors[0]);

        assertEquals("count of descriptors references", 1, descriptorRefs.length);
        assertEquals("reference must be project", "project", descriptorRefs[0]);

        assertFalse("We must not ignore permissions problems", source.isIgnorePermissions());

        String outputDir = params.getOutputDirectory();

        assertStartsWithDir(outputDir, source.getOutputDirectory());
        assertStartsWithDir(outputDir, source.getWorkingDirectory());
        assertStartsWithDir(outputDir, source.getTemporaryRootDirectory());
    }

    private boolean containsDir(String outputDir, File path) {
        return path.toString().contains(outputDir + File.separator);
    }
    
    private void assertStartsWithDir(String outputDir, File path) {
        String expectedStartsWith = outputDir + File.separator;
        int length = expectedStartsWith.length();
        assertEquals(expectedStartsWith, path.toString().substring(0, length));
    }
}
