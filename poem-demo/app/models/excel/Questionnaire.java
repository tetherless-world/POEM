package models.excel;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import models.Instrument;
import models.Item;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public class Questionnaire {
    private Instrument instrument;

    public Questionnaire(Instrument instrument) {
        this.instrument = instrument;
    }

    // Generate XLSX with item position and label columns
    public byte[] toXLSX() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // Sheet 1: Items
            Sheet itemsSheet = workbook.createSheet("Items");
            Row header = itemsSheet.createRow(0);
            header.createCell(0).setCellValue("Item Position");
            header.createCell(1).setCellValue("Item Stem Text");

            List<Item> items = instrument.getItems();
            if (items != null) {
                int rowIdx = 1;
                for (Item item : items) {
                    Row row = itemsSheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(item.getPosition());
                    row.createCell(1).setCellValue(item.getLabel());
                }
            }

            // Sheet 2: Other Components
            Sheet compSheet = workbook.createSheet("Other Components");
            Row compHeader = compSheet.createRow(0);
            compHeader.createCell(0).setCellValue("Component");
            //compHeader.createCell(1).setCellValue("Text");

            List<models.Component> components = instrument.getComponents();
            if (components != null) {
                int rowIdx = 1;
                for (models.Component comp : components) {
                    Row row = compSheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(comp.getLabel());
                    //row.createCell(1).setCellValue(""); // Text column left blank for now
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
            return new byte[0];
        }
    }
}
