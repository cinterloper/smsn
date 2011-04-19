package net.fortytwo.myotherbrain.flashcards.db;

import net.fortytwo.myotherbrain.flashcards.Card;
import net.fortytwo.myotherbrain.flashcards.Deck;

import java.io.IOException;
import java.util.Collection;

/**
 * User: josh
 * Date: 3/26/11
 * Time: 7:35 PM
 */
public interface CardStore<Q, A> {
    void add(Card<Q, A> card) throws IOException;

    Card<Q, A> find(Deck<Q, A> deck,
                    String cardName);

    CloseableIterator<Card<Q, A>> findAll(Deck<Q, A> deck);

    void clear();
}