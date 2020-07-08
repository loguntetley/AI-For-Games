package games.coltexpress;

import core.AbstractParameters;
import core.AbstractGameState;
import core.actions.AbstractAction;
import core.components.Component;
import core.components.Deck;
import core.interfaces.IGamePhase;
import core.interfaces.IPrintable;
import games.coltexpress.cards.ColtExpressCard;
import games.coltexpress.cards.RoundCard;
import games.coltexpress.components.Compartment;
import games.coltexpress.components.Loot;
import core.components.PartialObservableDeck;
import games.coltexpress.ColtExpressTypes.*;
import utilities.Pair;

import java.util.*;

import static core.CoreConstants.PARTIAL_OBSERVABLE;

public class ColtExpressGameState extends AbstractGameState implements IPrintable {

    // Colt express adds 4 game phases
    public enum ColtExpressGamePhase implements IGamePhase {
        PlanActions,
        ExecuteActions,
        DraftCharacter
    }

    // Cards in player hands
    List<Deck<ColtExpressCard>> playerHandCards;
    // A deck for each player
    List<Deck<ColtExpressCard>> playerDecks;
    List<Deck<Loot>> playerLoot;
    int[] bulletsLeft;
    // The player characters available
    HashMap<Integer, CharacterType> playerCharacters;
    int playerPlayingBelle;

    // The card stack built by players each round
    PartialObservableDeck<ColtExpressCard> plannedActions;
    // The train to loot
    LinkedList<Compartment> trainCompartments;
    // The round cards
    Deck<RoundCard> rounds;

    @Override
    public List<Component> _getAllComponents() {
        List<Component> components = new ArrayList<>();
        components.add(plannedActions);
        components.addAll(trainCompartments);

        for (Compartment compartment: trainCompartments) {
            components.add(compartment.getLootInside());
            components.add(compartment.getLootOnTop());
        }

        components.addAll(playerHandCards);
        components.addAll(playerDecks);
        components.addAll(playerLoot);
        components.add(rounds);
        return components;
    }

    @Override
    protected AbstractGameState _copy(int playerId) {
        ColtExpressGameState copy = new ColtExpressGameState(gameParameters, getNPlayers());

        // These are always visible
        copy.bulletsLeft = bulletsLeft.clone();
        copy.playerCharacters = new HashMap<>(playerCharacters);
        copy.playerPlayingBelle = playerPlayingBelle;

        // These are modified in PO
        copy.playerHandCards = new ArrayList<>();
        for (Deck<ColtExpressCard> d: playerHandCards) {
            copy.playerHandCards.add(d.copy());
        }
        copy.playerDecks = new ArrayList<>();
        for (Deck<ColtExpressCard> d: playerDecks) {
            copy.playerDecks.add(d.copy());
        }
        copy.playerLoot = new ArrayList<>();
        for (Deck<Loot> d: playerLoot) {
            copy.playerLoot.add(d.copy());
        }
        copy.plannedActions = plannedActions.copy();
        copy.rounds = rounds.copy();
        copy.trainCompartments = new LinkedList<>();
        for (Compartment d: trainCompartments) {
            copy.trainCompartments.add((Compartment) d.copy());
        }

        if (PARTIAL_OBSERVABLE && playerId != -1) {
            Random r = new Random(copy.gameParameters.getRandomSeed());
            for (int i = 0; i < getNPlayers(); i++) {
                if (i != playerId) {
                    // Other player hands are hidden, but it's known what's in a player's deck
                    // Shuffle together and deal new hands for opponents (same hand size)
                    copy.playerDecks.get(i).add(copy.playerHandCards.get(i));
                    int nCardsInHand = copy.playerHandCards.get(i).getSize();
                    copy.playerHandCards.get(i).clear();
                    copy.playerDecks.get(i).shuffle(r);
                    for (int j = 0; j < nCardsInHand; j++) {
                        copy.playerHandCards.get(i).add(copy.playerDecks.get(i).draw());
                    }
                }
                // All loot is hidden
                Deck<Loot> dLoot = copy.playerLoot.get(i);
                dLoot.clear();
                for (int j = 0; j < playerLoot.get(i).getSize(); j++) {
//                    dLoot.add(new Loot(LootType.Unknown, 0));  // Unknown loot

                    // Random value for loot of this same type
                    Loot realLoot = playerLoot.get(i).get(j);
                    ArrayList<Pair<Integer,Integer>> lootOptions = ((ColtExpressParameters)copy.gameParameters).loot.get(realLoot.getLootType());
                    int randomValue = lootOptions.get(r.nextInt(lootOptions.size())).a;
                    dLoot.add(new Loot(realLoot.getLootType(), randomValue));
                }
            }

            // All loot in train is also hidden
            for (int i = 0; i < trainCompartments.size(); i++) {
                Compartment realCompartment = trainCompartments.get(i);
                Compartment copyCompartment = copy.trainCompartments.get(i);
                copyCompartment.lootOnTop.clear();
                copyCompartment.lootInside.clear();
                for (int j = 0; j < realCompartment.lootOnTop.getSize(); j++) {
                    // Random value for loot of this same type
                    Loot realLoot = realCompartment.lootOnTop.get(j);
                    ArrayList<Pair<Integer,Integer>> lootOptions = ((ColtExpressParameters)copy.gameParameters).loot.get(realLoot.getLootType());
                    int randomValue = lootOptions.get(r.nextInt(lootOptions.size())).a;
                    copyCompartment.lootOnTop.add(new Loot(realLoot.getLootType(), randomValue));
                }
                for (int j = 0; j < realCompartment.lootInside.getSize(); j++) {
                    // Random value for loot of this same type
                    Loot realLoot = realCompartment.lootInside.get(j);
                    ArrayList<Pair<Integer,Integer>> lootOptions = ((ColtExpressParameters)copy.gameParameters).loot.get(realLoot.getLootType());
                    int randomValue = lootOptions.get(r.nextInt(lootOptions.size())).a;
                    copyCompartment.lootInside.add(new Loot(realLoot.getLootType(), randomValue));
                }
            }

            // Some planned actions may be hidden, put them back in owner player's deck, shuffle decks and replace with
            // random options
            HashMap<Integer, ArrayList<Integer>> cardReplacements = new HashMap<>();
            for (int i = 0; i < plannedActions.getSize(); i++) {
                if (!plannedActions.isComponentVisible(i, playerId)) {
                    int p = plannedActions.get(i).playerID;
                    if (!cardReplacements.containsKey(p)) {
                        cardReplacements.put(p, new ArrayList<>());
                    }
                    cardReplacements.get(p).add(i);
                    copy.playerDecks.get(p).add(plannedActions.get(i));
                }
            }
            for (Map.Entry<Integer, ArrayList<Integer>> e: cardReplacements.entrySet()) {
                copy.playerDecks.get(e.getKey()).shuffle(r);
                for (int i: e.getValue()) {
                    // TODO: This might be a bullet card...
                    copy.plannedActions.setComponent(i, copy.playerDecks.get(e.getKey()).draw());
                }
            }

            // Round cards are hidden for subsequent rounds, randomize those
            for (int i = getTurnOrder().getRoundCounter()+1; i < rounds.getSize(); i++) {
                if (i != rounds.getSize() -1) {
                    copy.rounds.setComponent(i, getRandomRoundCard((ColtExpressParameters) getGameParameters(), i));
                } else {
                    copy.rounds.setComponent(i, getRandomEndRoundCard((ColtExpressParameters) getGameParameters(), i));
                }
            }
        }
        return copy;
    }

    @Override
    protected double _getScore(int playerId) {
        return new ColtExpressHeuristic().evaluateState(this, playerId);
    }

    @Override
    protected ArrayList<Integer> _getUnknownComponentsIds(int playerId) {
        return new ArrayList<Integer>() {{
            // Other player hands and decks are not visible
            // All loot is not visible
            for (int i = 0; i < getNPlayers(); i++) {
                if (i != playerId) {
                    add(playerHandCards.get(i).getComponentID());
                    add(playerDecks.get(i).getComponentID());
                }
                add(playerLoot.get(i).getComponentID());
            }
            for (Compartment c: trainCompartments) {
                add(c.lootInside.getComponentID());
                add(c.lootOnTop.getComponentID());
            }
            // Following round cards are not visible
            for (int i = getTurnOrder().getRoundCounter()+1; i < rounds.getSize(); i++) {
                add(rounds.getComponents().get(i).getComponentID());
            }
            // Some planned actions might not be visible
            for (int i = 0; i < plannedActions.getSize(); i++) {
                if (!plannedActions.isComponentVisible(i, playerId)) {
                    add(plannedActions.getComponents().get(i).getComponentID());
                }
            }
        }};
    }

    @Override
    protected void _reset() {
        playerHandCards = new ArrayList<>();
        playerDecks = new ArrayList<>();
        playerLoot = new ArrayList<>();
        bulletsLeft = new int[getNPlayers()];
        playerCharacters = new HashMap<>();
        playerPlayingBelle = -1;
        plannedActions = null;
        trainCompartments = new LinkedList<>();
        rounds = new Deck<>("Rounds", -1);
        gamePhase = ColtExpressGamePhase.PlanActions;
    }

    public ColtExpressGameState(AbstractParameters gameParameters, int nPlayers) {
        super(gameParameters, new ColtExpressTurnOrder(nPlayers, (ColtExpressParameters) gameParameters));
        gamePhase = ColtExpressGamePhase.PlanActions;
        trainCompartments = new LinkedList<>();
        playerPlayingBelle = -1;
    }

    public void addLoot(Integer playerID, Loot loot) {
        playerLoot.get(playerID).add(loot);
    }

    public void addNeutralBullet(Integer playerID) {
        addBullet(playerID, -1);
    }

    public void addBullet(Integer playerID, Integer shooterID) {
        this.playerDecks.get(playerID).add(new ColtExpressCard(shooterID, ColtExpressCard.CardType.Bullet));
        if (playerCharacters.containsKey(shooterID))
            bulletsLeft[shooterID]--;
    }

    public LinkedList<Compartment> getTrainCompartments() {
        return trainCompartments;
    }

    public Deck<Loot> getLoot(int playerID){return playerLoot.get(playerID);}

    public PartialObservableDeck<ColtExpressCard> getPlannedActions() {
        return plannedActions;
    }

    public List<Deck<ColtExpressCard>> getPlayerDecks() {
        return playerDecks;
    }

    public Deck<RoundCard> getRounds() {
        return rounds;
    }

    @Override
    public void printToConsole() {
        System.out.println("Colt Express Game-State");
        System.out.println("=======================");

        int currentPlayer = turnOrder.getCurrentPlayer(this);

        for (int i = 0; i < getNPlayers(); i++){
            if (currentPlayer == i)
                System.out.print(">>> ");
            System.out.print("Player " + i + " = "+ playerCharacters.get(i).name() + ":  ");
            System.out.print("Hand=");
            System.out.print(playerHandCards.get(i).toString());
            System.out.print("; Deck=");
            System.out.print(playerDecks.get(i).toString());
            System.out.print("; Loot=");
            System.out.print(playerLoot.get(i).toString());
            System.out.println();
        }
        System.out.println();
        System.out.println(printTrain());

        System.out.println();
        System.out.print("Planned Actions: ");
        System.out.println(plannedActions.toString());

        System.out.println();
        int i = 0;
        for (RoundCard round : rounds.getComponents()){
            if (i == ((ColtExpressTurnOrder)turnOrder).getCurrentRoundCardIndex()) {
                System.out.print("->");
            }
            System.out.print(round.toString());
            System.out.print(", ");
            i++;
        }

        System.out.println();
        System.out.println("Current GamePhase: " + gamePhase);
    }

    public String printTrain(){
        StringBuilder sb = new StringBuilder();
        sb.append("Train:\n");
        for (Compartment compartment : trainCompartments)
        {
            sb.append(compartment.toString());
            sb.append("\n");
        }
        sb.deleteCharAt(sb.length()-1);

        return sb.toString();
    }

    /**
     * Helper getter methods for round card composition.
     */

    RoundCard getRandomEndRoundCard(ColtExpressParameters cep, int i) {
        int nEndCards = cep.endRoundCards.length;
        int choice = new Random(cep.getRandomSeed() + i).nextInt(nEndCards);
        return getEndRoundCard(cep, choice);
    }

    RoundCard getEndRoundCard(ColtExpressParameters cep, int idx) {
        if (idx >= 0 && idx < cep.endRoundCards.length) {
            RoundCard.TurnType[] turnTypes = cep.endRoundCards[idx].getTurnTypeSequence();
            AbstractAction event = cep.endRoundCards[idx].getEndCardEvent();
            return new RoundCard(cep.endRoundCards[idx].name(), turnTypes, event);
        }
        return null;
    }

    RoundCard getRandomRoundCard(ColtExpressParameters cep, int i) {
        int nRoundCards = cep.roundCards.length;
        int choice = new Random(cep.getRandomSeed() + i).nextInt(nRoundCards);
        return getRoundCard(cep, choice, getNPlayers());
    }

    RoundCard getRoundCard(ColtExpressParameters cep, int idx, int nPlayers) {
        if (idx >= 0 && idx < cep.roundCards.length) {
            RoundCard.TurnType[] turnTypes = cep.roundCards[idx].getTurnTypeSequence(nPlayers);
            AbstractAction event = cep.roundCards[idx].getEndCardEvent();
            return new RoundCard(cep.roundCards[idx].name(), turnTypes, event);
        }
        return null;
    }

}
