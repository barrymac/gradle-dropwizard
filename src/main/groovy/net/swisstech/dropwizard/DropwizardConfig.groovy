package net.swisstech.dropwizard

/**
 * Created by anantagarwal on 21/05/2015.
 */
public class DropwizardConfig {
    /** all ports of the config */
    Set<Integer> ports = [] as Set

    /**
     * base urls reconstructed from config.
     */
    Map<String, String> urls = [:]
}
