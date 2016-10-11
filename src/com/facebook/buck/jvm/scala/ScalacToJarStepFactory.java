/*
 * Copyright 2015-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.scala;

import com.facebook.buck.io.ProjectFilesystem;
import com.facebook.buck.jvm.core.SuggestBuildRules;
import com.facebook.buck.jvm.java.BaseCompileToJarStepFactory;
import com.facebook.buck.jvm.java.ClassUsageFileWriter;
import com.facebook.buck.model.BuildTarget;
import com.facebook.buck.rules.BuildContext;
import com.facebook.buck.rules.BuildRule;
import com.facebook.buck.rules.BuildRuleResolver;
import com.facebook.buck.rules.BuildableContext;
import com.facebook.buck.rules.RuleKeyObjectSink;
import com.facebook.buck.rules.SourcePathResolver;
import com.facebook.buck.rules.Tool;
import com.facebook.buck.step.Step;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;

import java.nio.file.Path;

public class ScalacToJarStepFactory extends BaseCompileToJarStepFactory {

  public static final Function<BuildContext, Iterable<Path>> EMPTY_EXTRA_CLASSPATH =
      new Function<BuildContext, Iterable<Path>>() {
        @Override
        public Iterable<Path> apply(BuildContext input) {
          return ImmutableList.of();
        }
      };

  public static ImmutableList<String> collectScalacArguments(
      ScalaBuckConfig config,
      BuildRuleResolver resolver,
      ImmutableList<String> extraArguments) {

    return ImmutableList.<String>builder()
        .addAll(config.getCompilerFlags())
        .addAll(extraArguments)
        .addAll(
            Iterables.transform(
                resolver.getAllRules(config.getCompilerPlugins()),
                new Function<BuildRule, String>() {
                  @Override public String apply(BuildRule input) {
                    return "-Xplugin:" + input.getPathToOutput();
                  }
                })
        )
        .build();
  }

  private final Tool scalac;
  private final ImmutableList<String> extraArguments;
  private final Function<BuildContext, Iterable<Path>> extraClassPath;

  public ScalacToJarStepFactory(Tool scalac, ImmutableList<String> extraArguments) {
    this(scalac, extraArguments, EMPTY_EXTRA_CLASSPATH);
  }

  public ScalacToJarStepFactory(
      Tool scalac,
      ImmutableList<String> extraArguments,
      Function<BuildContext, Iterable<Path>> extraClassPath) {
    this.scalac = scalac;
    this.extraArguments = extraArguments;
    this.extraClassPath = extraClassPath;
  }

  @Override
  public void createCompileStep(
      BuildContext context,
      ImmutableSortedSet<Path> sourceFilePaths,
      BuildTarget invokingRule,
      SourcePathResolver resolver,
      ProjectFilesystem filesystem,
      ImmutableSortedSet<Path> classpathEntries,
      Path outputDirectory,
      Optional<Path> workingDirectory,
      Path pathToSrcsList,
      Optional<SuggestBuildRules> suggestBuildRules,
      ClassUsageFileWriter usedClassesFileWriter,
      /* out params */
      ImmutableList.Builder<Step> steps,
      BuildableContext buildableContext) {

    steps.add(
      new ScalacStep(
          scalac,
          extraArguments,
          resolver,
          outputDirectory,
          sourceFilePaths,
          ImmutableSortedSet.<Path>naturalOrder()
            .addAll(Optional.fromNullable(extraClassPath.apply(context))
                .or(ImmutableList.of()))
            .addAll(classpathEntries)
            .build(),
          filesystem));
  }

  @Override
  public void appendToRuleKey(RuleKeyObjectSink sink) {
    scalac.appendToRuleKey(sink);
    sink.setReflectively("extraArguments", extraArguments);
  }
}
