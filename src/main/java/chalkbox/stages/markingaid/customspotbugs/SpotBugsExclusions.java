package chalkbox.stages.markingaid.customspotbugs;

import java.util.List;

/**
 * Class holds information used to build an xml filter file for spotbugs that can be used as part of -exclude or
 * -include argument
 * @param classes
 * @param patterns
 * @param codes
 * @param categories
 */
public record SpotBugsExclusions(
        List<String> classes,
        List<String> patterns,
        List<String> codes,
        List<String> categories
) {

    public SpotBugsExclusions(
            List<String> classes,
            List<String> patterns,
            List<String> codes,
            List<String> categories
    ) {
        this.classes = classes;
        this.patterns = patterns;
        this.codes = codes;
        this.categories = categories;
    }

    public boolean noExclusions() {
        return classes.isEmpty() && patterns.isEmpty() && codes.isEmpty() && categories.isEmpty();
    }

}
