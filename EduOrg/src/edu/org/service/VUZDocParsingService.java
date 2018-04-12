package edu.org.service;

import by.i4t.exceptions.BusinessConditionException;
import by.i4t.exceptions.DataValidationException;
import by.i4t.exceptions.ImportDataException;
import by.i4t.helper.EduDocsStatus;
import by.i4t.objects.*;
import by.i4t.objects.bo.VUZEduDocLineItem;
import by.i4t.parser.VuzDocImportFileParser;
import edu.org.validators.VUZEduDocValidator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service("vuzDocParsingService")
public class VUZDocParsingService {
    @Autowired
    private VUZEduDocValidator vuzEduDocValidator;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private CitizenService citizenService;
    private List<Integer> docsWithoutSeria = new ArrayList<Integer>();

    private List<VUZEduDocLineItem> importErrorsList = new ArrayList<VUZEduDocLineItem>();

    public VUZDocParsingService() {
        docsWithoutSeria.add(17);
        docsWithoutSeria.add(18);
        docsWithoutSeria.add(19);
        docsWithoutSeria.add(20);
        docsWithoutSeria.add(37);
    }

    public void execute(ImportedFile importedFile) throws ImportDataException {
        getImportErrorsList().clear();

        FileContent impFileContent = repositoryService.getFileContentRepository().findOne(importedFile.getImportedFileContent());
        if (impFileContent == null)
            return;

        List<VUZEduDocLineItem> lineItemList = new VuzDocImportFileParser().execute(impFileContent.getContent());
        for (VUZEduDocLineItem LI : lineItemList)
            buildDocument(LI);

        XSSFWorkbook errorFile = createErrorExcelFile();

        try {
            importedFile.setSuccessRowCount(lineItemList.size() - importErrorsList.size());
            importedFile.setErrorCount(importErrorsList.size());

            if (importErrorsList.size() != 0) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                errorFile.write(baos);
                errorFile.close();

                FileContent errorFileContent = new FileContent();
                errorFileContent.setID(UUID.randomUUID());
                errorFileContent.setContent(baos.toByteArray());
                repositoryService.getFileContentRepository().save(errorFileContent);
                importedFile.setErrorFileContent(errorFileContent.getID());
            } else
                errorFile.close();

            importedFile.setStatus(1);
            repositoryService.getImportedFileRepository().save(importedFile);
        } catch (IOException e) {
            throw new ImportDataException("ErrorFile creation error!", e);
        }
    }

    private XSSFWorkbook createErrorExcelFile() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();

        // ---

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");

        // ---

        for (VUZEduDocLineItem item : getImportErrorsList()) {
            Row row = sheet.createRow(getImportErrorsList().indexOf(item));
            try {
                row.createCell(0).setCellValue(item.getSecondName());
                row.createCell(1).setCellValue(item.getFirstName());
                row.createCell(2).setCellValue(item.getPatronymic());
                row.createCell(3).setCellValue(item.getPersonalID());
                row.createCell(4).setCellValue(item.getEducationLevel());
                row.createCell(5).setCellValue(item.getEduOrg());

                //row.createCell(6).setCellValue(item.getEduStartDate());
                //row.createCell(7).setCellValue(item.getEduStopDate());
                row.createCell(6).setCellValue(sdf.format(item.getEduStartDate()));
                row.createCell(7).setCellValue(sdf.format(item.getEduStopDate()));

                row.createCell(8).setCellValue(item.getDocType());
                row.createCell(9).setCellValue(item.getEduDocSeria());
                row.createCell(10).setCellValue(item.getEduDocNumber());
                row.createCell(11).setCellValue(item.getEduDocRegNumber());

                //row.createCell(12).setCellValue(item.getEduDocIssueDate());
                row.createCell(12).setCellValue(sdf.format(item.getEduDocIssueDate()));

                row.createCell(13).setCellValue(item.getSpecialty());
                row.createCell(14).setCellValue(item.getSpecialization());
                row.createCell(15).setCellValue(item.getQualification());
                row.createCell(16).setCellValue(item.getMemberOfBel());
            } catch (Exception ex) {

            }
            row.createCell(16).setCellValue(item.getImportErrorMsg());
        }

        return workbook;
    }

    protected void buildDocument(VUZEduDocLineItem LI) {
        VUZDocument doc = new VUZDocument();
        try {
            doc.setDocType(vuzEduDocValidator.checkEduDocType(LI.getDocType()));

            if (!docsWithoutSeria.contains(doc.getDocType().getID()))
                doc.setDocSeria(vuzEduDocValidator.checkEduDocSeria(LI.getEduDocSeria()));

            doc.setCitizen(buildCitizenInfo(LI, isForeignStudent(doc.getDocType(), doc.getDocSeria())));
            buildEducationPeriod(LI);
//	    try {
//			LI.setEduStopDate(new SimpleDateFormat("dd.MM.yyyy").parse(LI.getEduDocIssueDate()));
//		} catch (ParseException e) {
//			throw new DataValidationException("Ошибка проверки данных: неправильный формат даты окончания обучения.");
//		}
            //////////////
            if (vuzEduDocValidator.checkEducationPeriod(LI.getEduStartDate(), LI.getEduStopDate())) {
                doc.setEduStartDate(LI.getEduStartDate());
                doc.setEduStopDate(LI.getEduStopDate());
            }
            doc.setDocNumber(vuzEduDocValidator.checkEduDocNumber(LI.getEduDocNumber()));
            doc.setDocRegNumber(vuzEduDocValidator.checkEduDocRegNumber(LI.getEduDocRegNumber()));
            doc.setDocIssueDate(vuzEduDocValidator.checkEduDocIssueDate(LI.getEduDocIssueDate()));

            doc.setEduOrganization(vuzEduDocValidator.checkEduOrg(LI.getEduOrg()));
            doc.setSpecialty(vuzEduDocValidator.checkSpecialty(LI.getSpecialty(), LI.getSpecialization(), LI.getQualification()));

            if (LI.getSpecialization() != null)
                doc.setSpecializationTXT(LI.getSpecialization());
            else
                doc.setSpecializationTXT("");

            if (LI.getQualification() != null)
                doc.setQualificationTXT(LI.getQualification());

            if (isForeignStudent(doc.getDocType(), doc.getDocSeria()))
                doc.setStatus(EduDocsStatus.FOREIGN_STUDENT.getCode());
            else
                doc.setStatus(EduDocsStatus.VALIDATED.getCode());

            doc.setChangeDate(new Date());

            if (!saveEduDoc(doc))
                handleError(LI, "Документ с указанными параметрами уже существует.");

        } catch (DataValidationException | BusinessConditionException ex) {
            handleError(LI, ex.getMessage());
        }
    }

    private Boolean isForeignStudent(EduDocType doctype, String docSeria) {
        return "ДИБ".equalsIgnoreCase(docSeria) || "ДИ".equalsIgnoreCase(docSeria) || doctype.getName().contains("иностранных");
    }

    public ByteArrayInputStream bulidXLSXDocument(List<VUZDocument> docList) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat("dd-MM-yyyy");
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet();
        Row row = sheet.createRow(0);
        row.createCell(0).setCellValue("Фамилия");
        row.createCell(1).setCellValue("Имя");
        row.createCell(2).setCellValue("Отчество");
        row.createCell(3).setCellValue("Идентификационный номер");
        row.createCell(4).setCellValue("Учреждение образования");
        row.createCell(5).setCellValue("Дата начала обучения");
        row.createCell(6).setCellValue("Дата окончания обучения");
        row.createCell(7).setCellValue("Тип документа");
        row.createCell(8).setCellValue("Серия документа");
        row.createCell(9).setCellValue("Номер документа");
        row.createCell(10).setCellValue("Дата выдачи документа");
        row.createCell(11).setCellValue("Номер записи в журнале регистрации");
        row.createCell(12).setCellValue("Специальность");
        row.createCell(13).setCellValue("Специализация");
        row.createCell(14).setCellValue("Квалификация");
        row.createCell(15).setCellValue("Гражданство");
        for (VUZDocument item : docList) {
            row = sheet.createRow(docList.indexOf(item) + 1);
            if (item.getCitizen() != null) {
                row.createCell(0).setCellValue(item.getCitizen().getSecondName());
                row.createCell(1).setCellValue(item.getCitizen().getFirstName());
                row.createCell(2).setCellValue(item.getCitizen().getPatronymic());
                row.createCell(3).setCellValue(item.getCitizen().getIdNumber());
                row.createCell(15).setCellValue(item.getCitizen().getMemberOfBel());
            }
            row.createCell(4).setCellValue(item.getEduOrganization().getName());
            row.createCell(5).setCellValue(dateFormatter.format(item.getEduStartDate()));
            row.createCell(6).setCellValue(dateFormatter.format(item.getEduStopDate()));
            if (item.getDocType() != null)
                row.createCell(7).setCellValue(item.getDocType().getName());
            row.createCell(8).setCellValue(item.getDocSeria());
            row.createCell(9).setCellValue(item.getDocNumber());
            row.createCell(10).setCellValue(dateFormatter.format(item.getDocIssueDate()));
            row.createCell(11).setCellValue(item.getDocRegNumber());
            if (item.getSpecialty() != null)
                row.createCell(12).setCellValue(item.getSpecialty().getName());
            if (item.getSpecializationTXT() != null)
                row.createCell(13).setCellValue(item.getSpecializationTXT());
            if (item.getQualificationTXT() != null)
                row.createCell(14).setCellValue(item.getQualificationTXT());
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            workbook.write(baos);
            workbook.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return new ByteArrayInputStream(baos.toByteArray());
    }

    private void buildEducationPeriod(VUZEduDocLineItem LI) throws DataValidationException {
        try {
            if (LI.getEduStartDate() == null) {
                if (LI.getEduStartDateString() != null && !LI.getEduStartDateString().isEmpty())
                    LI.setEduStartDate(new SimpleDateFormat("dd.MM.yyyy").parse(LI.getEduStartDateString()));
            }

            if (LI.getEduStopDate() == null) {
                if (LI.getEduStopDateString() != null && !LI.getEduStopDateString().isEmpty())
                    LI.setEduStopDate(new SimpleDateFormat("dd.MM.yyyy").parse(LI.getEduStopDateString()));
            }
        } catch (ParseException ex) {
            throw new DataValidationException("Invalid education period format: " + ex.getMessage());
        }
    }

    private Citizen buildCitizenInfo(VUZEduDocLineItem LI, Boolean isForeignStudent) throws DataValidationException, BusinessConditionException {
        if (isForeignStudent) {
            vuzEduDocValidator.checkPersonalNameForeignStudent(LI.getFirstName(), LI.getSecondName(), LI.getPatronymic(), LI.getMemberOfBel());
            return citizenService.createNew(LI.getFirstName(), LI.getSecondName(), LI.getPatronymic(), LI.getMemberOfBel());
        } else {
            vuzEduDocValidator.checkPersonalName(LI.getFirstName(), LI.getSecondName(), LI.getPatronymic(), LI.getMemberOfBel());
            String personalID = vuzEduDocValidator.checkPersonalID(LI.getPersonalID());
            return citizenService.createNew(LI.getFirstName(), LI.getSecondName(), LI.getPatronymic(), LI.getPersonalID(), LI.getMemberOfBel());
        }
    }

    public Boolean saveEduDoc(VUZDocument doc) throws BusinessConditionException {
        // 1. duplication diplom
//    	if (doc.getDocType().getName().contains("дубликат"))
//    	{
//    	    Calendar calendar = Calendar.getInstance();
//    	    calendar.setTime(doc.getEduStopDate());
        //
//    	    Integer year = calendar.get(Calendar.YEAR);
//    	    calendar.clear();
//    	    calendar.set(Calendar.YEAR, year);
//    	    Date l_stop = calendar.getTime();
        //
//    	    calendar.add(Calendar.YEAR, 1);
//    	    Date h_stop = calendar.getTime();
        //
//    	    Criterion criterion = Restrictions.and(Restrictions.eq("citizen", doc.getCitizen()), Restrictions.eq("specialty", doc.getSpecialty()),
//    		    Restrictions.between("edu_stop_date", l_stop, h_stop), Restrictions.eq("eduOrganization", doc.getEduOrganization()));
//    	    List<VUZDocument> docList = (List<VUZDocument>) commonDAO.commonRequest(VUZDocument.class, criterion, null);
        //
//    	    if (docList.isEmpty())
//    		throw new BusinessConditionException("Оригинальный диплом не найден!");
        //
//    	    if (docList.size() > 1)
//    		throw new BusinessConditionException("Найдено " + docList.size() + " оригинальных дипломов!");
        //
//    	    VUZDocument originalDoc = docList.get(0);
//    	    doc.setGisunID(originalDoc.getGisunID());
//    	}

        // 2. if doc is exist
        List<VUZDocument> docList = repositoryService.getVuzDocumentRepository()
                .findByDocNumberAndDocTypeAndEduOrganization(doc.getDocNumber(), doc.getDocType(), doc.getEduOrganization());
        if (docList.size() > 1)
            throw new BusinessConditionException("Найдено " + docList.size() + " дипломов с указаным номером!");

        if (docList.size() == 1) {
            VUZDocument originalDoc = docList.get(0);
//            if (!originalDoc.getCitizen().getIdNumber().equals(doc.getCitizen().getIdNumber()))
//                throw new BusinessConditionException("Диплом с таким номером выдан другому человеку " + doc.getDocSeria() + doc.getDocNumber() );
            originalDoc.setChangeDate(new Date());
            originalDoc.setCitizen(doc.getCitizen());
            originalDoc.setDocRegNumber(doc.getDocRegNumber());
            originalDoc.setDocType(doc.getDocType());
            originalDoc.setEduStartDate(doc.getEduStartDate());
            originalDoc.setEduStopDate(doc.getEduStopDate());
            originalDoc.setQualification(doc.getQualification());
            originalDoc.setSpecialization(doc.getSpecialization());
            originalDoc.setSpecializationTXT(doc.getSpecializationTXT());
            originalDoc.setSpecialty(doc.getSpecialty());
            originalDoc.setStatus(-6);
            repositoryService.getVuzDocumentRepository().save(originalDoc);
            return true;
        }
        repositoryService.getVuzDocumentRepository().save(doc);
        return true;

//    	Criterion criterion = Restrictions.and(Restrictions.eq("docNumber", doc.getDocNumber()), Restrictions.eq("docSeria", doc.getDocSeria()),
//    		Restrictions.eq("docIssueDate", doc.getDocIssueDate()), Restrictions.eq("eduOrganization", doc.getEduOrganization()));
//    	List<VUZDocument> docList = (List<VUZDocument>) commonDAO.commonRequest(VUZDocument.class, criterion, null);
        //
//    	if (docList != null && !docList.isEmpty())
//    	    return false;
        //
//    	commonDAO.saveOrUpdate(doc);
//    	return true;
    }

    private void handleError(VUZEduDocLineItem LI, String msg) {
        LI.setImportErrorMsg(msg);
        getImportErrorsList().add(LI);
    }

    /**
     * @return the importErrorsList
     */
    public List<VUZEduDocLineItem> getImportErrorsList() {
        return importErrorsList;
    }
}
