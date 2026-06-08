package com.chalo.config;

import com.chalo.model.Tag;
import com.chalo.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the tags table on every startup. Each tag is checked by slug:
 *   - missing  → inserted
 *   - icon changed → updated (allows migrating emoji → BI class names)
 *   - up-to-date  → skipped
 *
 * Icon values are Bootstrap Icon class strings ("bi bi-fire").
 * Changing an entry here updates the DB on the next restart.
 * Dashboard and Host Adventure both query TagRepository, so they
 * pick up the change automatically with zero template edits.
 */
@Component
@RequiredArgsConstructor
public class TagDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TagDataLoader.class);

    private final TagRepository tagRepository;

    private record Seed(String name, String slug, String icon) {}

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("Trekking",       "trekking",       "bi bi-person-walking"),
            new Seed("Camping",        "camping",        "bi bi-fire"),
            new Seed("Photography",    "photography",    "bi bi-camera-fill"),
            new Seed("Road Trip",      "road-trip",      "bi bi-car-front-fill"),
            new Seed("Beach",          "beach",          "bi bi-sun-fill"),
            new Seed("Scuba Diving",   "scuba-diving",   "bi bi-water"),
            new Seed("Sunrise",        "sunrise",        "bi bi-sunrise"),
            new Seed("Waterfall",      "waterfall",      "bi bi-cloud-drizzle-fill"),
            new Seed("Cycling",        "cycling",        "bi bi-bicycle"),
            new Seed("Food Exploring", "food-exploring", "bi bi-egg-fried"),
            new Seed("Skydiving",      "skydiving",      "bi bi-wind"),
            new Seed("Bungee Jumping", "bungee-jumping", "bi bi-lightning-fill")
    );

    @Override
    @Transactional
    public void run(String... args) {
        int inserted = 0;
        int updated  = 0;

        for (Seed s : DEFAULTS) {
            var existing = tagRepository.findBySlug(s.slug());
            if (existing.isEmpty()) {
                tagRepository.save(Tag.builder()
                        .name(s.name()).slug(s.slug()).icon(s.icon()).build());
                inserted++;
            } else {
                Tag tag = existing.get();
                if (!s.icon().equals(tag.getIcon())) {
                    tag.setIcon(s.icon()); // dirty-check emits UPDATE on tx commit
                    updated++;
                }
            }
        }

        if (inserted > 0) log.info("TagDataLoader: inserted {} tag(s).", inserted);
        if (updated  > 0) log.info("TagDataLoader: updated icon for {} tag(s).", updated);
    }
}
