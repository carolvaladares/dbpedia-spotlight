package org.dbpedia.spotlight.string

import org.dbpedia.spotlight.exceptions.InputException
import org.dbpedia.spotlight.model.{SurfaceFormOccurrence, Text}

/**
 * Created by IntelliJ IDEA.
 * User: Max
 * Contains functions to limit the amount of context for disambiguation.
 */

//TODO this narrows context down to *approximately* maxContextWords with an error margin of 2 words

class ContextExtractor(minContextWords : Int, maxContextWords : Int) {

    private val spaceChars = Set(' ', '\n', '\t')

    /**
     * Strips the context of a SurfaceFormOccurrence
     */
    def narrowContext(occ : SurfaceFormOccurrence) : SurfaceFormOccurrence = {
        val sb = new StringBuffer(occ.surfaceForm.name)  //StringBuilder is more efficient but not thread-safe

        var l = occ.textOffset - 1                            //left-hand char of surface form
        var r = occ.textOffset + occ.surfaceForm.name.length  //right-hand char of surface form

        var wordCount = 0
        while((wordCount < maxContextWords) && !(isEnd(occ.context.text, l, false) && isEnd(occ.context.text, r, true))) {

            //consume words to the left
            val newL = consume(occ.context.text, l, sb, false)
            if(newL != l) {
                wordCount += 1
            }
            l = newL

            //consume words to the right
            val newR = consume(occ.context.text, r, sb, true)
            if(newR != r) {
                wordCount += 1
            }
            r = newR
        }

        if(wordCount < minContextWords) {
            throw new InputException("not enough context: need at least "+minContextWords+" words, found "+wordCount)
        }

        new SurfaceFormOccurrence(occ.surfaceForm, new Text(sb.toString), scala.math.max(0, occ.textOffset-l-1))
    }

    /**
     * Consumes white space and one word after that and adds everything to the StringBuffer.
     * Returns the position that was not consumed yet.
     */
    private def consume(text : String, pos : Int, sb : StringBuffer, readDirection : Boolean) : Int = {
        var newPos = pos
        newPos = consume(text, newPos, sb, readDirection, (s, p) =>  isSpace(s, p))  //consume spaces
        newPos = consume(text, newPos, sb, readDirection, (s, p) => !isSpace(s, p))  //consume letters
        newPos
    }

    private def isSpace(text : String, pos : Int) = spaceChars.contains(text(pos))

    private def consume(text : String, pos : Int, sb : StringBuffer, readDirection : Boolean, charCheck : (String,Int)=>Boolean) : Int = {
        var newPos = pos
        while(!isEnd(text, newPos, readDirection) && charCheck(text, newPos)) {
            addToString(sb, text(newPos), readDirection)
            newPos = updatePosition(newPos, readDirection)
        }
        newPos
    }

    // Returns true if pos is not a valid index of text. The readDirection flag specifies in which direction.
    private def isEnd(text : String, pos : Int, readDirection : Boolean) = if(readDirection) pos >= text.length else pos < 0

    private def addToString(sb : StringBuffer, ch : Char, readDirection : Boolean) = if(readDirection) sb.append(ch) else sb.insert(0, ch)

    private def updatePosition(pos : Int, readDirection : Boolean) = if(readDirection) pos + 1 else pos - 1

}