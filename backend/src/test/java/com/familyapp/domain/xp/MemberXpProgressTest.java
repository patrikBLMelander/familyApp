package com.familyapp.domain.xp;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemberXpProgressTest {

    private MemberXpProgress withXp(int xp) {
        return new MemberXpProgress(
                UUID.randomUUID(), UUID.randomUUID(), 2026, 9,
                xp, MemberXpProgress.calculateLevel(xp), 0,
                OffsetDateTime.now(), OffsetDateTime.now()
        );
    }

    @Test
    void noStarsBeforeAndAtMaxLevel() {
        assertThat(MemberXpProgress.calculateStars(0)).isZero();
        assertThat(MemberXpProgress.calculateStars(124)).isZero();
        assertThat(MemberXpProgress.calculateStars(125)).isZero();
        assertThat(MemberXpProgress.calculateStars(174)).isZero();
    }

    @Test
    void starsAppearEveryFiftyXpPastMax() {
        assertThat(MemberXpProgress.calculateStars(175)).isEqualTo(1);
        assertThat(MemberXpProgress.calculateStars(224)).isEqualTo(1);
        assertThat(MemberXpProgress.calculateStars(225)).isEqualTo(2);
        assertThat(MemberXpProgress.calculateStars(375)).isEqualTo(5);
    }

    @Test
    void starsAreCappedForDisplayButMilestonesAreNot() {
        assertThat(MemberXpProgress.calculateStars(1000)).isEqualTo(MemberXpProgress.MAX_STARS);
        // 525 XP = (525-125)/50 = 8 milestones -> tickets keep coming.
        assertThat(MemberXpProgress.milestonesPastMax(525)).isEqualTo(8);
    }

    @Test
    void xpToNextStarCountsDownWithinEachBand() {
        assertThat(withXp(100).getXpToNextStar()).isZero();      // not at max yet
        assertThat(withXp(125).getXpToNextStar()).isEqualTo(50); // first star at 175
        assertThat(withXp(150).getXpToNextStar()).isEqualTo(25);
        assertThat(withXp(175).getXpToNextStar()).isEqualTo(50); // just earned one, next at 225
        assertThat(withXp(200).getXpToNextStar()).isEqualTo(25);
    }

    @Test
    void starsInstanceMatchesStaticCalc() {
        assertThat(withXp(300).getStars()).isEqualTo(3);
    }
}
