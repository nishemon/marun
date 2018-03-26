package jp.cccis.marun.configure;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.ivy.plugins.resolver.BintrayResolver;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.apache.ivy.plugins.resolver.RepositoryResolver;

import lombok.Data;

@Data
public class Repository {
	private String baseurl;
	private String type = "maven";
	private String name;

	public static RepositoryResolver build(final Repository conf) throws IllegalConfigurationException {
		switch (conf.name) {
		case "jcenter":
		case "bintray":
			if (conf.baseurl == null) {
				conf.baseurl = "https://jcenter.bintray.com/";
			}
			break;
		case "central":
			if (conf.baseurl == null) {
				conf.baseurl = IBiblioResolver.DEFAULT_M2_ROOT;
			}
		}
		URI root;
		try {
			root = new URI(conf.baseurl);
		} catch (URISyntaxException e) {
			throw new IllegalConfigurationException("Invalid baseurl '%s'", conf.baseurl);
		}
		if (root.getHost().contentEquals("jcenter.bintray.com")) {
			return new BintrayResolver();
		}
		switch (conf.type.toLowerCase()) {
		case "maven":
			IBiblioResolver resolver = new IBiblioResolver();
			resolver.setM2compatible(true);
			resolver.setName(root.getHost());
			resolver.setRoot(root.toString());
			return resolver;
		}
		throw new IllegalConfigurationException("Invalid type '%s'", conf.type);
	}
}