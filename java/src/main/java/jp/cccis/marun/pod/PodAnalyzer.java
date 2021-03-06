package jp.cccis.marun.pod;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jp.cccis.marun.pod.ResourceFinder.Entry;
import lombok.Getter;

public class PodAnalyzer {
	private ClassLoader loader;
	private List<Class<?>> loadedClasses;
	@Getter
	private final Map<String, List<String>> failureClasses = new LinkedHashMap<>();
	@Getter
	private final Map<String, List<String>> duplicateEntries = new LinkedHashMap<>();
	private final List<Entry> resources = new ArrayList<>();

	public PodAnalyzer(final List<Path> jarfiles) throws ClassNotFoundException, IOException {
		this.loader = URLClassLoader.newInstance(jarfiles.stream().map(p -> {
			try {
				return p.toUri().toURL();
			} catch (MalformedURLException e) {
				throw new IllegalStateException(e);
			}
		}).toArray(URL[]::new), ClassLoader.getSystemClassLoader().getParent());
		List<Entry> allResources = listupAllResources(jarfiles, this.duplicateEntries);
		this.loadedClasses = loadAll(allResources, this.loader, this.resources, this.failureClasses);
	}

	private static List<Entry> listupAllResources(final List<Path> jars, final Map<String, List<String>> dup)
			throws IOException {
		List<Entry> resources = new ArrayList<>();
		Set<String> uniq = new HashSet<>();
		for (Path p : jars) {
			for (Entry ent : ResourceFinder.findResourcesInJarFile(p)) {
				String fullname;
				if (ent.getPackageName().isEmpty()) {
					fullname = ent.getResourceName();
				} else {
					fullname = ent.getPackageName() + '.' + ent.getResourceName();
				}
				if (uniq.add(fullname)) {
					resources.add(ent);
				} else {
					List<String> paths = dup.computeIfAbsent(fullname, f -> new ArrayList<>());
					paths.add(ent.getFilepath().toString());
				}
			}
		}
		return resources;
	}

	private static List<Class<?>> loadAll(final List<Entry> allResources, final ClassLoader loader,
			final List<Entry> noclasses, final Map<String, List<String>> failures) throws ClassNotFoundException {
		List<Class<?>> classes = new ArrayList<>();
		for (Entry ent : allResources) {
			String res = ent.getResourceName();
			if (res.endsWith(".class")) {
				String cname = res.substring(0, res.length() - ".class".length());
				String fullcname;
				if (ent.getPackageName().isEmpty()) {
					fullcname = cname;
				} else {
					fullcname = ent.getPackageName() + "." + cname;
				}
				try {
					classes.add(loader.loadClass(fullcname));
				} catch (NoClassDefFoundError | SecurityException e) {
					failures.computeIfAbsent(e.getMessage().replace('/', '.'), c -> new ArrayList<>()).add(fullcname);
				}
			} else {
				noclasses.add(ent);
			}
		}
		return classes;
	}

	public List<Method> findStaticMethod(final String name, final Class<?>... classes) {
		Objects.requireNonNull(name);
		List<Method> methods = new ArrayList<>();
		for (Class<?> clazz : this.loadedClasses) {
			try {
				methods.add(clazz.getDeclaredMethod(name, classes));
			} catch (NoSuchMethodException e) {
				continue;
			} catch (SecurityException e) {
				// TODO logging
				continue;
			} catch (NoClassDefFoundError e) {
				List<String> failures = this.failureClasses.computeIfAbsent(e.getMessage().replace('/', '.'),
						c -> new ArrayList<>());
				failures.add(clazz.getName() + "#" + name);
			}
		}
		return methods;
	}

	public Map<Path, List<String>> getEmbededResources(final String packageNamePattern) {
		Objects.requireNonNull(packageNamePattern);
		Map<Path, List<String>> jarResources = new LinkedHashMap<>();
		Pattern namePattern = Pattern.compile(packageNamePattern);
		for (Entry ent : this.resources) {
			String packageName = ent.getPackageName().replace('.', '/');
			if (namePattern.matcher(packageName).matches()) {
				List<String> list = jarResources.computeIfAbsent(ent.getFilepath(), p -> new ArrayList<>());
				if (packageName.isEmpty()) {
					list.add(ent.getResourceName());
				} else {
					list.add(String.format("%s/%s", packageName, ent.getResourceName()));
				}
			}
		}
		return jarResources;
	}
}
