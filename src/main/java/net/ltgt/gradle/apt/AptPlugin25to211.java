package net.ltgt.gradle.apt;

import static net.ltgt.gradle.apt.CompatibilityUtils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.Nullable;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;

class AptPlugin25to211 extends AptPlugin.Impl {

  @Override
  protected AptPlugin.AptConvention createAptConvention(
      Project project, AbstractCompile task, CompileOptions compileOptions) {
    return new AptConvention25to211(project);
  }

  @Override
  protected void configureCompileTask(
      Project project, final AbstractCompile task, final CompileOptions compileOptions) {
    property(
        getInputs(task),
        "aptOptions.annotationProcessing",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptConvention25to211.class)
                .getAptOptions()
                .isAnnotationProcessing();
          }
        });
    optionalProperty(
        getInputs(task),
        "aptOptions.processors",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptConvention25to211.class)
                .getAptOptions()
                .getProcessors();
          }
        });
    optionalProperty(
        getInputs(task),
        "aptOptions.processorArgs",
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptConvention25to211.class)
                .getAptOptions()
                .getProcessorArgs();
          }
        });

    files(
        getInputs(task),
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptPlugin.AptConvention.class)
                .getAptOptions()
                .getProcessorpath();
          }
        });

    dir(
        getOutputs(task),
        new Callable<Object>() {
          @Override
          public Object call() {
            return task.getConvention()
                .getPlugin(AptPlugin.AptConvention.class)
                .getGeneratedSourcesDestinationDir();
          }
        });

    task.doFirst(
        new Action<Task>() {
          @Override
          public void execute(Task task) {
            AptConvention25to211 convention =
                task.getConvention().getPlugin(AptConvention25to211.class);
            convention.makeDirectories();
            compileOptions.getCompilerArgs().addAll(convention.asArguments());
          }
        });
  }

  @Override
  protected AptPlugin.AptSourceSetConvention createAptSourceSetConvention(
      Project project, SourceSet sourceSet) {
    return new AptSourceSetConvention25to211(project, sourceSet);
  }

  @Override
  protected void ensureCompileOnlyConfiguration(
      final Project project,
      final SourceSet sourceSet,
      AptPlugin.AptSourceSetConvention convention) {
    Configuration configuration =
        project.getConfigurations().create(convention.getCompileOnlyConfigurationName());
    configuration.setVisible(false);
    configuration.setDescription("Compile-only classpath for " + sourceSet.getName());
    configuration.extendsFrom(
        project.getConfigurations().findByName(sourceSet.getCompileConfigurationName()));

    sourceSet.setCompileClasspath(configuration);

    if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
      final Configuration conf = configuration;
      project
          .getPlugins()
          .withType(
              JavaPlugin.class,
              new Action<JavaPlugin>() {
                @Override
                public void execute(JavaPlugin javaPlugin) {
                  sourceSet.setCompileClasspath(
                      project.files(
                          project
                              .getConvention()
                              .getPlugin(JavaPluginConvention.class)
                              .getSourceSets()
                              .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                              .getOutput(),
                          conf));
                }
              });
    }
  }

  @Override
  protected Configuration ensureAnnotationProcessorConfiguration(
      Project project, SourceSet sourceSet, AptPlugin.AptSourceSetConvention convention) {
    Configuration annotationProcessorConfiguration =
        project.getConfigurations().create(convention.getAnnotationProcessorConfigurationName());
    annotationProcessorConfiguration.setVisible(false);
    annotationProcessorConfiguration.setDescription(
        "Annotation processors and their dependencies for " + sourceSet.getName() + ".");
    return annotationProcessorConfiguration;
  }

  @Override
  protected void configureCompileTaskForSourceSet(
      Project project,
      final SourceSet sourceSet,
      AbstractCompile task,
      CompileOptions compileOptions) {
    AptPlugin.AptConvention convention =
        task.getConvention().getPlugin(AptPlugin.AptConvention.class);
    convention
        .getAptOptions()
        .setProcessorpath(
            new Callable<FileCollection>() {
              @Override
              public FileCollection call() {
                return new DslObject(sourceSet)
                    .getConvention()
                    .getPlugin(AptPlugin.AptSourceSetConvention.class)
                    .getAnnotationProcessorPath();
              }
            });
    convention.setGeneratedSourcesDestinationDir(
        new Callable<File>() {
          @Override
          public File call() {
            return new DslObject(sourceSet.getOutput())
                .getConvention()
                .getPlugin(AptPlugin.AptSourceSetOutputConvention.class)
                .getGeneratedSourcesDir();
          }
        });
  }

  private static class AptSourceSetConvention25to211 extends AptPlugin.AptSourceSetConvention {
    private FileCollection annotationProcessorPath;

    private AptSourceSetConvention25to211(Project project, SourceSet sourceSet) {
      super(project, sourceSet);
    }

    @Nullable
    @Override
    public FileCollection getAnnotationProcessorPath() {
      return annotationProcessorPath;
    }

    @Override
    public void setAnnotationProcessorPath(@Nullable FileCollection annotationProcessorPath) {
      this.annotationProcessorPath = annotationProcessorPath;
    }

    @Override
    public String getCompileOnlyConfigurationName() {
      return sourceSet.getCompileConfigurationName() + "Only";
    }

    @Override
    public String getAnnotationProcessorConfigurationName() {
      // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
      return sourceSet.getTaskName("", "annotationProcessor");
    }
  }

  private static class AptConvention25to211 extends AptPlugin.AptConvention {
    private final Project project;

    private final AptOptions25to211 aptOptions;

    private Object generatedSourcesDestinationDir;

    AptConvention25to211(Project project) {
      this.project = project;
      this.aptOptions = new AptOptions25to211(project);
    }

    @Override
    @Nullable
    public File getGeneratedSourcesDestinationDir() {
      if (generatedSourcesDestinationDir == null) {
        return null;
      }
      return project.file(generatedSourcesDestinationDir);
    }

    @Override
    public void setGeneratedSourcesDestinationDir(@Nullable Object generatedSourcesDestinationDir) {
      this.generatedSourcesDestinationDir = generatedSourcesDestinationDir;
    }

    @Override
    public AptPlugin.AptOptions getAptOptions() {
      return aptOptions;
    }

    void makeDirectories() {
      if (generatedSourcesDestinationDir != null) {
        project.mkdir(generatedSourcesDestinationDir);
      }
    }

    List<String> asArguments() {
      List<String> result = new ArrayList<>();
      if (generatedSourcesDestinationDir != null) {
        result.add("-s");
        result.add(getGeneratedSourcesDestinationDir().getPath());
      }
      if (aptOptions.processorpath != null && !aptOptions.getProcessorpath().isEmpty()) {
        result.add("-processorpath");
        result.add(aptOptions.getProcessorpath().getAsPath());
      }
      result.addAll(aptOptions.asArguments());
      return result;
    }
  }

  private static class AptOptions25to211 extends AptPlugin.AptOptions {
    private final Project project;

    private Object processorpath;

    private AptOptions25to211(Project project) {
      this.project = project;
    }

    @Nullable
    @Override
    public FileCollection getProcessorpath() {
      if (processorpath == null) {
        return null;
      }
      return project.files(processorpath);
    }

    @Override
    public void setProcessorpath(@Nullable Object processorpath) {
      this.processorpath = processorpath;
    }
  }
}
