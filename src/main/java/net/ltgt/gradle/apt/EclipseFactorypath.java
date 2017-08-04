package net.ltgt.gradle.apt;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.util.ConfigureUtil;

import java.util.Collection;

public class EclipseFactorypath {
  private final XmlFileContentMerger file;

  public EclipseFactorypath(XmlFileContentMerger file) {
    this.file = file;
  }

  private Collection<Configuration> plusConfigurations;
  private Collection<Configuration> minusConfigurations;

  public Collection<Configuration> getPlusConfigurations() {
    return plusConfigurations;
  }

  public void setPlusConfigurations(Collection<Configuration> plusConfigurations) {
    this.plusConfigurations = plusConfigurations;
  }

  public Collection<Configuration> getMinusConfigurations() {
    return minusConfigurations;
  }

  public void setMinusConfigurations(Collection<Configuration> minusConfigurations) {
    this.minusConfigurations = minusConfigurations;
  }

  public XmlFileContentMerger getFile() {
    return this.file;
  }

  public void file(Closure<? super XmlFileContentMerger> closure) {
    ConfigureUtil.configure(closure, this.file);
  }

  public void file(Action<? super XmlFileContentMerger> action) {
    action.execute(this.file);
  }
}
