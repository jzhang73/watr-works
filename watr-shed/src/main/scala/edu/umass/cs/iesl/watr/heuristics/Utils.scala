package edu.umass.cs.iesl.watr
package heuristics

import TypeTags._
import geometry.LTBounds
import Constants._
import textreflow.TextReflowF.TextReflow
import textreflow.data._

import scala.collection.mutable.ListBuffer
import scala.io.Source
import scala.util.control.Breaks._

object Utils {

    def isOfFirstNameInitialFormat(authorNameComponent: String): Boolean = {

        if(NAME_INITIAL_FORMAT_PATTERN.findFirstIn(authorNameComponent).getOrElse(BLANK).length.==(authorNameComponent.length)){
            return true
        }
        false
    }

    def getNextComponentIndices(currentSeparateComponentIndex: Int, currentComponentIndex: Int, currentComponent: String): (Int, Int) = {

        if (currentSeparateComponentIndex + 1 == currentComponent.split(SPACE_SEPARATOR).length) {
            return (0, currentComponentIndex + 1)
        }
        (currentSeparateComponentIndex + 1, currentComponentIndex)
    }

    def getStartIndexAfterComma(currentComponent: String): Int = {

        var indexAfterComma: Int = currentComponent.indexOf(COMMA)

        while (!currentComponent.charAt(indexAfterComma).isLetter) {
            indexAfterComma += 1
        }

        indexAfterComma
    }

    def getReflowString(textReflow: TextReflow): String = {
        textReflow.charAtoms().map {
            charAtom => charAtom.char
        }.mkString
    }

    def getIndexesForComponents(component: String, textReflow: TextReflow, indexRange: (Int, Int)): (Int, Int) = {

        var componentIndex: Int = 0
        var textReflowIndex: Int = indexRange._1
        var componentStartIndex: Int = -1

        while (componentIndex < component.length && textReflowIndex < indexRange._2) {
            val currentTextReflowChar: String = textReflow.charAtoms()(textReflowIndex).char
            var localReflowCharIndex: Int = 0
            while (localReflowCharIndex < currentTextReflowChar.length) {
                if (component.charAt(componentIndex).==(currentTextReflowChar.charAt(localReflowCharIndex))) {
                    if (componentStartIndex.==(-1)) {
                        componentStartIndex = textReflowIndex
                    }
                    componentIndex += 1
                }
                else {
                    componentIndex = 0
                    componentStartIndex = -1
                }
                localReflowCharIndex += 1
            }
            textReflowIndex += 1
        }

        (componentStartIndex, textReflowIndex)
    }

    def getBoundingBoxesWithIndexesFromReflow(indexes: (Int, Int), textReflow: TextReflow): LTBounds = {
        LTBounds(FloatRep(textReflow.charAtoms()(indexes._1).bbox.left.asInstanceOf[Int]), FloatRep(textReflow.charAtoms()(indexes._1).bbox.top.asInstanceOf[Int]),
            FloatRep(textReflow.charAtoms()(indexes._2 - 1).bbox.right.asInstanceOf[Int] - textReflow.charAtoms()(indexes._1).bbox.left.asInstanceOf[Int]), FloatRep(textReflow.charAtoms()(indexes._1).bbox.height.asInstanceOf[Int]))
    }

    def getBoundingBoxesForComponents(name: NameWithBBox, geometricallySeparatedName: String, textReflow: TextReflow): NameWithBBox = {

        val (nameStartIndex, nameEndIndex) = getIndexesForComponents(component = geometricallySeparatedName.replace(SPACE_SEPARATOR, BLANK), textReflow = textReflow, (0, textReflow.charAtoms().length))
        name.bbox = getBoundingBoxesWithIndexesFromReflow((nameStartIndex, nameEndIndex), textReflow)
        if (name.firstName.componentText.nonEmpty) {
            val (firstNameStartIndex, firstNameEndIndex) = getIndexesForComponents(component = name.firstName.componentText.replace(SPACE_SEPARATOR, BLANK), textReflow = textReflow, (nameStartIndex, nameEndIndex))
            name.firstName.componentBBox = getBoundingBoxesWithIndexesFromReflow((firstNameStartIndex, firstNameEndIndex), textReflow)
        }
        if (name.middleName.componentText.nonEmpty) {
            val (middleNameStartIndex, middleNameEndIndex) = getIndexesForComponents(component = name.middleName.componentText.replace(SPACE_SEPARATOR, BLANK), textReflow = textReflow, (nameStartIndex, nameEndIndex))
            name.middleName.componentBBox = getBoundingBoxesWithIndexesFromReflow((middleNameStartIndex, middleNameEndIndex), textReflow)
        }
        if (name.lastName.componentText.nonEmpty) {
            val (lastNameStartIndex, lastNameEndIndex) = getIndexesForComponents(component = name.lastName.componentText.replace(SPACE_SEPARATOR, BLANK), textReflow = textReflow, (nameStartIndex, nameEndIndex))
            name.lastName.componentBBox = getBoundingBoxesWithIndexesFromReflow((lastNameStartIndex, lastNameEndIndex), textReflow)
        }
        name
    }

    def isLetterString(charSequence: String): Boolean = {
        charSequence.foreach {
            character => {
                if (!character.isLetter) return false
            }
        }
        true
    }

    def isNumberString(charSequence: String): Boolean = {
        charSequence.foreach {
            character => {
                if (!character.isDigit) return false
            }
        }
        true
    }

    def getYPosition(textReflow: TextReflow): Int = {

        val yPositionCounts: scala.collection.mutable.Map[Int, Int] = scala.collection.mutable.Map[Int, Int]()

        for(charAtom <- textReflow.charAtoms()){
            if(yPositionCounts.get(charAtom.bbox.top.asInstanceOf[Int]).isEmpty){
                yPositionCounts += (charAtom.bbox.top.asInstanceOf[Int] -> 0)
            }
            yPositionCounts.update(charAtom.bbox.top.asInstanceOf[Int], yPositionCounts(charAtom.bbox.top.asInstanceOf[Int])+1)

        }

        val maxRecord: (Int, Int) = yPositionCounts.maxBy(_._2)
        if(maxRecord._2 > 1){
            return maxRecord._1
        }
        -1
    }

    def getMatchedKeywordsForAffiliationComponent(affiliationComponent: String): ListBuffer[String] = {

        val matchedKeywords: ListBuffer[String] = new ListBuffer[String]()

        if (EMAIL_PATTERN.findFirstIn(affiliationComponent).getOrElse(BLANK).length.!=(0)){
            matchedKeywords += EMAIL_KEYWORD
        }

        if (matchedKeywords.isEmpty){
            breakable{
                RESOURCE_KEYWORDS.foreach{
                    resource => {
                        for (line <- Source.fromInputStream(getClass.getResourceAsStream(resource._2)).getLines){
                            if ("\\b".concat(line).concat("\\b").r.findFirstIn(affiliationComponent).isDefined){
                                matchedKeywords += resource._1
                                break
                            }
                        }
                    }
                }
            }
        }

        matchedKeywords

    }

    def cleanSeparatedComponent(separatedComponent: String): String = {

        var cleanedComponent: String = separatedComponent

        cleanedComponent = separatedComponent.substring(CLEANUP_PATTERN.findFirstIn(separatedComponent).getOrElse(BLANK).length, separatedComponent.length)

        if(separatedComponent.takeRight(1).equals(PERIOD)){
            cleanedComponent = separatedComponent.dropRight(1)
        }
        if(cleanedComponent.head.==(DOT)){
            cleanedComponent = separatedComponent.tail
        }

        cleanedComponent.trim

    }

    def getMatchedZipCodePatterns(affiliationComponent: String): ListBuffer[String] = {

        val matchedZipCodePatterns: ListBuffer[String] = new ListBuffer[String]()

        ZIP_CODE_PATTERNS.foreach{
            zipCodePattern => {
                zipCodePattern._2.foreach{
                    zipCodeRegex => {
                        if (zipCodeRegex.findFirstIn(affiliationComponent).getOrElse(BLANK).nonEmpty){
                            matchedZipCodePatterns += zipCodePattern._1
                        }
                    }
                }
            }
        }

        matchedZipCodePatterns

    }

    def isPresentInAuthors(component: String, authorNames: ListBuffer[NameWithBBox]): Boolean = {

        for (authorName <- authorNames) {
            if (component.contains(authorName.lastName.componentText.toLowerCase) || component.contains(authorName.middleName.componentText.toLowerCase) || component.contains(authorName.firstName.componentText.toLowerCase)){
                return true
            }
        }
        false
    }

}
