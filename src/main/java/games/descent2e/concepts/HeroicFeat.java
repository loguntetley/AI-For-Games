package games.descent2e.concepts;

import games.descent2e.actions.DescentAction;
import games.descent2e.actions.herofeats.*;

public enum HeroicFeat {
    DoubleAttack (new DoubleAttack()),
    MagicAttackAll (new AttackAllAdjacent()),
    HealAll (new HealAllInRange()),
    StunMonsters (new StunAllInMonsterGroup()),
    FreeMoveFriend (new HeroicFeatExtraMovement()),
    ExtraAttack (new HeroicFeatExtraAttack()),
    Teleport (new RemoveFromMap()),
    RunAttack (new DoubleMoveAttack());

    public final DescentAction action;
    HeroicFeat(DescentAction action) {
        this.action = action;
    }
}