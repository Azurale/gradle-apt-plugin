package net.ltgt.gradle.apt;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class AptPlugin implements Plugin<Project> {
  @Override
  public void apply(final Project project) {
    configureCompileTasks(project, JavaCompile.class, new GetCompileOptions<JavaCompile>() {
      @Override
      public CompileOptions getCompileOptions(JavaCompile task) {
        return task.getOptions();
      }
    });
    configureCompileTasks(project, GroovyCompile.class, new GetCompileOptions<GroovyCompile>() {
      @Override
      public CompileOptions getCompileOptions(GroovyCompile task) {
        return task.getOptions();
      }
    });

    project.getPlugins().withType(JavaBasePlugin.class, new Action<JavaBasePlugin>() {
      @Override
      public void execute(JavaBasePlugin javaBasePlugin) {
        final JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        javaConvention.getSourceSets().all(new Action<SourceSet>() {
          @Override
          public void execute(final SourceSet sourceSet) {
            AptSourceSetConvention convention = new AptSourceSetConvention(project, sourceSet);
            new DslObject(sourceSet).getConvention().getPlugins().put("net.ltgt.apt", convention);

            new DslObject(sourceSet.getOutput()).getConvention().getPlugins().put("net.ltgt.apt", new AptSourceSetOutputConvention(project, sourceSet));

            String compileOnlyConfigurationName = convention.getCompileOnlyConfigurationName();
            // Gradle 2.12 already creates such a configuration in the JavaBasePlugin; our compileOnlyConfigurationName has the same value
            Configuration configuration = project.getConfigurations().findByName(compileOnlyConfigurationName);
            if (configuration == null) {
              configuration = project.getConfigurations().create(compileOnlyConfigurationName);
              configuration.setVisible(false);
              configuration.setDescription("Compile-only classpath for ${sourceSet}.");
              configuration.extendsFrom(project.getConfigurations().findByName(sourceSet.getCompileConfigurationName()));

              sourceSet.setCompileClasspath(configuration);

              // Special-case the JavaPlugin's 'test' source set, only if we created the testCompileOnly configuration
              // Note that Gradle 2.12 actually creates a testCompilationClasspath configuration that extends testCompileOnly
              // and sets it as sourceSets.test.compileClasspath; rather than directly using the testCompileOnly configuration.
              if (SourceSet.TEST_SOURCE_SET_NAME.equals(sourceSet.getName())) {
                final Configuration conf = configuration;
                project.getPlugins().withType(JavaPlugin.class, new Action<JavaPlugin>() {
                  @Override
                  public void execute(JavaPlugin javaPlugin) {
                    sourceSet.setCompileClasspath(project.files(javaConvention.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput(), conf));
                  }
                });
              }
            }

            Configuration aptConfiguration = project.getConfigurations().create(convention.getAptConfigurationName());
            aptConfiguration.setVisible(false);
            aptConfiguration.setDescription("Processor path for ${sourceSet}");

            configureCompileTask(project, sourceSet, sourceSet.getCompileJavaTaskName());
          }
        });
      }
    });
    project.getPlugins().withType(GroovyBasePlugin.class, new Action<GroovyBasePlugin>() {
      @Override
      public void execute(GroovyBasePlugin groovyBasePlugin) {
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        javaConvention.getSourceSets().all(new Action<SourceSet>() {
          @Override
          public void execute(SourceSet sourceSet) {
            configureCompileTask(project, sourceSet, sourceSet.getCompileTaskName("groovy"));
          }
        });
      }
    });
  }

  private interface GetCompileOptions<T extends AbstractCompile> {
    CompileOptions getCompileOptions(T task);
  }

  private <T extends AbstractCompile> void configureCompileTasks(final Project project, Class<T> compileTaskClass, final GetCompileOptions<T> getCompileOptions) {
    project.getTasks().withType(compileTaskClass, new Action<T>() {
      @Override
      public void execute(final T task) {
        task.getConvention().getPlugins().put("net.ltgt.apt", new AptConvention(project));
        task.getInputs().property("aptOptions.annotationProcessing", new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            return task.getConvention().getPlugin(AptConvention.class).getAptOptions().isAnnotationProcessing();
          }
        });
        task.getInputs().property("aptOptions.processors", new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            return task.getConvention().getPlugin(AptConvention.class).getAptOptions().getProcessors();
          }
        });
        task.getInputs().property("aptOptions.processorArgs", new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            return task.getConvention().getPlugin(AptConvention.class).getAptOptions().getProcessorArgs();
          }
        });

        TaskInputs inputs = task.getInputs().files(new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            return task.getConvention().getPlugin(AptConvention.class).getAptOptions().getProcessorpath();
          }
        });
        if (inputs != task.getInputs()) {
          ((TaskInputFilePropertyBuilder) inputs).withPropertyName("aptOptions.processorpath");
        }

        TaskOutputs outputs = task.getOutputs().dir(new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            return task.getConvention().getPlugin(AptConvention.class).getGeneratedSourcesDestinationDir();
          }
        });
        if (outputs != task.getOutputs()) {
          ((TaskOutputFilePropertyBuilder) outputs).withPropertyName("generatedSourcesDestinationDir").optional();
        }

        task.doFirst(new Action<Task>() {
          @Override
          @SuppressWarnings("unchecked")
          public void execute(Task task) {
            AptConvention aptConvention = task.getConvention().getPlugin(AptConvention.class);
            aptConvention.makeDirectories();
            getCompileOptions.getCompileOptions((T) task).getCompilerArgs().addAll(aptConvention.buildCompilerArgs());
          }
        });
      }
    });
  }

  private void configureCompileTask(Project project, final SourceSet sourceSet, String taskName) {
    AbstractCompile task = project.getTasks().withType(AbstractCompile.class).getByName(taskName);
    AptConvention aptConvention = task.getConvention().getPlugin(AptConvention.class);
    aptConvention.setGeneratedSourcesDestinationDir(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        return new DslObject(sourceSet.getOutput()).getConvention().getPlugin(AptSourceSetOutputConvention.class).getGeneratedSourcesDir();
      }
    });
    aptConvention.aptOptions.setProcessorpath(new Callable<Object>() {
      @Override
      public Object call() throws Exception {
        return new DslObject(sourceSet).getConvention().getPlugin(AptSourceSetConvention.class).getProcessorpath();
      }
    });
  }

  public static class AptConvention {
    private final Project project;

    public AptConvention(Project project) {
      this.project = project;
      this.aptOptions = new AptOptions(project);
    }

    private Object generatedSourcesDestinationDir;

    public File getGeneratedSourcesDestinationDir() {
      if (generatedSourcesDestinationDir == null) {
        return null;
      }
      return project.file(generatedSourcesDestinationDir);
    }

    public void setGeneratedSourcesDestinationDir(Object generatedSourcesDestinationDir) {
      this.generatedSourcesDestinationDir = generatedSourcesDestinationDir;
    }

    private final AptOptions aptOptions;

    public AptOptions getAptOptions() {
      return aptOptions;
    }

    void makeDirectories() {
      if (generatedSourcesDestinationDir != null) {
        project.mkdir(generatedSourcesDestinationDir);
      }
    }

    List<String> buildCompilerArgs() {
      List<String> result = new ArrayList<>();
      if (generatedSourcesDestinationDir != null) {
        result.add("-s");
        result.add(getGeneratedSourcesDestinationDir().getPath());
      }
      if (!aptOptions.isAnnotationProcessing()) {
        result.add("-proc:none");
      }
      if (aptOptions.processorpath != null && !aptOptions.getProcessorpath().isEmpty()) {
        result.add("-processorpath");
        result.add(aptOptions.getProcessorpath().getAsPath());
      }
      if (aptOptions.processors != null && !aptOptions.getProcessors().isEmpty()) {
        result.add("-processor");
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object processor : aptOptions.getProcessors()) {
          if (!first) {
            sb.append(',');
          } else {
            first = false;
          }
          sb.append(processor);
        }
        result.add(sb.toString());
      }
      if (aptOptions.getProcessorArgs() != null) {
        for (Map.Entry<String, ?> entry : aptOptions.getProcessorArgs().entrySet()) {
          result.add("-A" + entry.getKey() + "=" + entry.getValue());
        }
      }
      return result;
    }
  }

  public static class AptOptions {
    private final Project project;

    public AptOptions(Project project) {
      this.project = project;
    }

    private boolean annotationProcessing = true;

    public boolean isAnnotationProcessing() {
      return annotationProcessing;
    }

    public void setAnnotationProcessing(boolean annotationProcessing) {
      this.annotationProcessing = annotationProcessing;
    }

    private Object processorpath;

    public FileCollection getProcessorpath() {
      if (processorpath == null) {
        return null;
      }
      return project.files(processorpath);
    }

    public void setProcessorpath(Object processorpath) {
      this.processorpath = processorpath;
    }

    private List<?> processors = new ArrayList<>();

    public List<?> getProcessors() {
      return processors;
    }

    public void setProcessors(List<?> processors) {
      this.processors = processors;
    }

    private Map<String, ?> processorArgs = new LinkedHashMap<>();

    public Map<String, ?> getProcessorArgs() {
      return processorArgs;
    }

    public void setProcessorArgs(Map<String, ?> processorArgs) {
      this.processorArgs = processorArgs;
    }
  }

  public static class AptSourceSetConvention {
    private final Project project;
    private final SourceSet sourceSet;

    public AptSourceSetConvention(final Project project, SourceSet sourceSet) {
      this.project = project;
      this.sourceSet = sourceSet;
      this.processorpath = new Callable<Object>() {
        @Override
        public Object call() throws Exception {
          return project.getConfigurations().findByName(getAptConfigurationName());
        }
      };
    }

    private Object processorpath;

    public FileCollection getProcessorpath() {
      if (processorpath == null) {
        return null;
      }
      return project.files(processorpath);
    }

    public void setProcessorpath(Object processorpath) {
      this.processorpath = processorpath;
    }

    public String getCompileOnlyConfigurationName() {
      return sourceSet.getCompileConfigurationName() + "Only";
    }

    public String getAptConfigurationName() {
      // HACK: we use the same naming logic/scheme as for tasks, so just use SourceSet#getTaskName
      return sourceSet.getTaskName("", "apt");
    }
  }

  public static class AptSourceSetOutputConvention {
    private final Project project;

    public AptSourceSetOutputConvention(final Project project, final SourceSet sourceSet) {
      this.project = project;
      this.generatedSourcesDir = new Callable<Object>() {
        @Override
        public Object call() throws Exception {
          return new File(project.getBuildDir(), "generated/source/apt/" + sourceSet.getName());
        }
      };
    }

    private Object generatedSourcesDir;

    public File getGeneratedSourcesDir() {
      if (generatedSourcesDir == null) {
        return null;
      }
      return project.file(generatedSourcesDir);
    }

    public void setGeneratedSourcesDir(Object generatedSourcesDir) {
      this.generatedSourcesDir = generatedSourcesDir;
    }
  }
}
