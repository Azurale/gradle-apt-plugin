package net.ltgt.gradle.apt

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

class AptPluginIntegrationSpec extends Specification {
  @Rule TemporaryFolder testProjectDir = new TemporaryFolder()
  File buildFile

  def setup() {
    buildFile = testProjectDir.newFile('build.gradle')
    buildFile << """\
      buildscript {
        dependencies {
          classpath files('${System.getProperty('plugin')}')
        }
      }
    """.stripIndent()
  }

  @Unroll
  def "simple java project, with Gradle #gradleVersion"() {
    given:
    buildFile << """\
      apply plugin: 'net.ltgt.apt'
      apply plugin: 'java'
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('src', 'main', 'java', 'simple'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('src', 'test', 'java', 'simple'), 'HelloWorldTest.java')
    f.createNewFile()
    f << """\
      package simple;

      public class HelloWorldTest {
        // Not a real unit-test
        public static void main(String[] args) {
          System.out.println(new HelloWorld().sayHello("World"));
        }
      }
    """.stripIndent()

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments('compileTestJava')
        .build()

    then:
    result.task(':compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':compileTestJava').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << ['2.5', '2.6', '2.7', '2.8', '2.9', '2.10', '2.11']
  }

  @Unroll
  def "simple java project with compile-only dependency, with Gradle #gradleVersion"() {
    given:
    def settingsFile = testProjectDir.newFile('settings.gradle')
    settingsFile << """\
      include 'annotations'
      include 'core'
    """.stripIndent()

    buildFile << """\
      subprojects {
        apply plugin: 'java'
      }
      project('core') {
        apply plugin: 'net.ltgt.apt'

        dependencies {
          compileOnly project(':annotations')
        }
      }
    """.stripIndent()

    def f = new File(testProjectDir.newFolder('annotations', 'src', 'main', 'java', 'annotations'), 'MyAnnotation.java')
    f.createNewFile()
    f << """\
      package annotations;

      import java.lang.annotation.Documented;

      public @interface MyAnnotation {
      }
    """.stripIndent()

    f = new File(testProjectDir.newFolder('core', 'src', 'main', 'java', 'core'), 'HelloWorld.java')
    f.createNewFile()
    f << """\
      package core;

      import annotations.MyAnnotation;

      @MyAnnotation
      public class HelloWorld {
        public String sayHello(String name) {
          return "Hello, " + name + "!";
        }
      }
    """.stripIndent()

    expect:

    when:
    def result = GradleRunner.create()
        .withGradleVersion(gradleVersion)
        .withProjectDir(testProjectDir.root)
        .withArguments(':core:javadoc')
        .build()

    then:
    result.task(':annotations:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:compileJava').outcome == TaskOutcome.SUCCESS
    result.task(':core:javadoc').outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << ['2.5', '2.6', '2.7', '2.8', '2.9', '2.10', '2.11']
  }
}
