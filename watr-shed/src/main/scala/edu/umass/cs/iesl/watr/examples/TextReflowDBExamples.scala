package edu.umass.cs.iesl.watr
package examples

import java.io.{BufferedWriter, FileOutputStream, OutputStreamWriter}

import corpora.DocumentCorpus
import docstore.{TextReflowDB, TextReflowDBTables}
import watrmarks.{Label, StandardLabels => LB}
import heuristics.GenericHeuristics._
import heuristics.AuthorNameHeuristics._
import heuristics.AffiliationsHeuristics._
import heuristics.Utils._

import textreflow.data._


class SampleDbCorpus {


    def authorNameSegmentation(targetDocumentStableId: String, targetLabel: Label) = {
        val textReflowDBTables = new TextReflowDBTables

        val textReflowDB = new TextReflowDB(tables = textReflowDBTables, dbname = "watr_works_db", dbuser = "watrworker", dbpass = "watrpasswd")
        val docStore: DocumentCorpus = textReflowDB.docStore

        val outputFileName: String = "/Users/BatComp/Desktop/UMass/IESL/Code/watr-works/author_segmentation.txt"
        val outputFileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFileName)))
        for {
            docStableId <- docStore.getDocuments(n = 1) if targetDocumentStableId.equals("") || docStableId.asInstanceOf[String].equals(targetDocumentStableId)
            docId <- docStore.getDocument(stableId = docStableId)
            labelId <- docStore.getZoneLabelsForDocument(docId = docId) if docStore.getLabel(labelId = labelId) == targetLabel
            zoneId <- docStore.getZonesForDocument(docId = docId, label = labelId)
            targetRegion <- docStore.getZone(zoneId = zoneId).regions
        } {
            println("Document: \t\t\t\t\t" + docStableId)
            outputFileWriter.write("Document: \t\t\t\t\t" + docStableId + "\n")
            val targetRegionTextReflow = docStore.getTextReflowForTargetRegion(regionId = targetRegion.id)
            if(targetRegionTextReflow.isDefined){
                outputFileWriter.write("Text Reflow: \t\t\t\t" + targetRegionTextReflow.get.toText() + "\n")
                val tokenizedNames = tokenizeTextReflow(targetRegionTextReflow.get)
                if(tokenizedNames.nonEmpty){
                    outputFileWriter.write("Tokenized: \t\t\t\t\t" + tokenizedNames + "\n")
                    val separateAuthorNamesByText = getSeparateAuthorNamesByText(tokenizedNames)
                    if(separateAuthorNamesByText.nonEmpty){
                        outputFileWriter.write("Text Separated: \t\t\t" + separateAuthorNamesByText + "\n")
                        val separateAuthorNamesByGeometry = getSeparateComponentsByGeometry(separateAuthorNamesByText, targetRegionTextReflow.get)
                        outputFileWriter.write("Geometrically Separated: \t" + separateAuthorNamesByGeometry + "\n")
                        val separateAuthorNameComponents = separateAuthorNamesByGeometry.map{
                            authorName => {
                                getSeparateAuthorNameComponents(authorName)
                            }
                        }
                        var nameIndex = 0
                        while(nameIndex < separateAuthorNamesByGeometry.length){
                            outputFileWriter.write("Bounding Box info: \t\t\t" + getBoundingBoxesForComponents(separateAuthorNameComponents(nameIndex), separateAuthorNamesByGeometry(nameIndex), textReflow = targetRegionTextReflow.get).toString + "\n")
                            nameIndex += 1
                        }
                    }
                }
            }
            outputFileWriter.write("------------------------------------------------------------------------------------------------\n")
        }
        outputFileWriter.close()
    }

    def exampleFunction1(targetDocumentStableId: String, targetLabel: Label) = {
        val textReflowDBTables = new TextReflowDBTables

        val textReflowDB = new TextReflowDB(tables = textReflowDBTables, dbname = "watr_works_db", dbuser = "watrworker", dbpass = "watrpasswd")
        val docStore: DocumentCorpus = textReflowDB.docStore

        for {
            docStableId <- docStore.getDocuments(n = 1) if (targetDocumentStableId.equals("") || docStableId.asInstanceOf[String].equals(targetDocumentStableId)) && !docStableId.asInstanceOf[String].equals("1609.07772.pdf.d")
            docId <- docStore.getDocument(stableId = docStableId)
            zone <- docStore.getDocumentZones(docId = docId, label = targetLabel)
            targetRegion <- zone.regions
        } {
            val targetRegionTextReflow = docStore.getTextReflowForTargetRegion(regionId = targetRegion.id)
            if(targetRegionTextReflow.isDefined){
                print("Document: \t\t\t\t\t" + docStableId + "\n")
                print("Text Reflow: \t\t\t\t" + targetRegionTextReflow.get.toText() + "\n")
                val tokenizedNames = tokenizeTextReflow(targetRegionTextReflow.get)
                if(tokenizedNames.nonEmpty){
                    print("Tokenized: \t\t\t\t\t" + tokenizedNames.mkString("||") + "\n")
                    val separateAuthorNamesByText = getSeparateAuthorNamesByText(tokenizedNames)
                    if(separateAuthorNamesByText.nonEmpty){
                        print("Text Separated: \t\t\t" + separateAuthorNamesByText .mkString("||")+ "\n")
                        val separateAuthorNamesByGeometry = getSeparateComponentsByGeometry(separateAuthorNamesByText, targetRegionTextReflow.get)
                        print("Geometrically Separated: \t" + separateAuthorNamesByGeometry.mkString("||") + "\n")
//                        val separateAuthorNameComponents = separateAuthorNamesByGeometry.map{
//                            authorName => {
//                                getSeparateAuthorNameComponents(authorName)
//                            }
//                        }
//                        var nameIndex = 0
//                        while(nameIndex < separateAuthorNamesByGeometry.length){
//                            print("Bounding Box info: \t\t\t" + getBoundingBoxesForComponents(separateAuthorNameComponents(nameIndex), separateAuthorNamesByGeometry(nameIndex), textReflow = targetRegionTextReflow.get).toString + "\n")
//                            nameIndex += 1
//                        }
                    }
                }
            }
            print("------------------------------------------------------------------------------------------------\n")
        }
    }

    def exampleFunction2(targetDocumentStableId: String, targetLabel: Label) = {
        val textReflowDBTables = new TextReflowDBTables

        val textReflowDB = new TextReflowDB(tables = textReflowDBTables, dbname = "watr_works_db", dbuser = "watrworker", dbpass = "watrpasswd")
        val docStore: DocumentCorpus = textReflowDB.docStore

        for {
            docStableId <- docStore.getDocuments(n = 1) if targetDocumentStableId.equals("") || docStableId.asInstanceOf[String].equals(targetDocumentStableId)
            docId <- docStore.getDocument(stableId = docStableId)
            zone <- docStore.getDocumentZones(docId = docId, label = targetLabel)
//            labelId <- docStore.getZoneLabelsForDocument(docId = docId) if docStore.getLabel(labelId = labelId) == targetLabel
//            zoneId <- docStore.getZonesForDocument(docId = docId, label = labelId)
            targetRegion <- zone.regions
        } {
            println("Document: \t\t\t\t\t" + docStableId)
            val targetRegionTextReflow = docStore.getTextReflowForTargetRegion(regionId = targetRegion.id)
            if(targetRegionTextReflow.isDefined){
                print("Text Reflow: \t\t\t\t" + targetRegionTextReflow.get.toText() + "\n")
                val tokenizedReflow = tokenizeTextReflow(targetRegionTextReflow.get)
                if(tokenizedReflow.nonEmpty){
                    print("Tokenized: \t\t\t\t\t" + tokenizedReflow.mkString("||") + "\n")
                    val affiliationsSeparatedByText = getSeparateAffiliationComponentsByText(tokenizedTextReflow = tokenizedReflow)
                    if(affiliationsSeparatedByText.nonEmpty){
                        print("Text Separated: \t\t\t" + affiliationsSeparatedByText.mkString("||") + "\n")
                        val affiliationsSeparatedByGeometry = getSeparateComponentsByGeometry(componentsSeparatedByText = affiliationsSeparatedByText, textReflow = targetRegionTextReflow.get)
                        if(affiliationsSeparatedByGeometry.nonEmpty){
                            print("Geometrically Separated: \t" + affiliationsSeparatedByGeometry.mkString("||") + "\n")
                        }
                    }
                }
            }
            print("------------------------------------------------------------------------------------------------\n")
        }
    }

    def removeDocuments() = {
        val textReflowDBTables = new TextReflowDBTables

        val textReflowDB = new TextReflowDB(tables = textReflowDBTables, dbname = "watr_works_db", dbuser = "watrworker", dbpass = "watrpasswd")
        val docStore: DocumentCorpus = textReflowDB.docStore

        for {
            docStableId <- docStore.getDocuments(n = 1)
        }{

        }
    }
}

object TextReflowDBExamples extends App {

    val dbCorpus = new SampleDbCorpus()
//    dbCorpus.authorNameSegmentation("", LB.Authors)
//    dbCorpus.exampleFunction1("", LB.Authors)
    dbCorpus.exampleFunction2("", LB.Affiliation)
// 0101047.pdf.d,

}
