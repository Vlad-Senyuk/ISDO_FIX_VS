package edu.org.service;

import by.i4t.exceptions.BusinessConditionException;
import by.i4t.exceptions.DataValidationException;
import by.i4t.objects.Citizen;
import by.i4t.objects.EduDocType;
import by.i4t.objects.VUZDocument;
import edu.org.models.EduDocDetailsDialogViewModel;
import edu.org.validators.VUZEduDocValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service("vuzDocTransformer")
public class VUZDocumentTransformer {
    @Autowired
    private CitizenService citizenService;
    @Autowired
    private RepositoryService repositoryService;
    private VUZEduDocValidator vuzEduDocValidator = new VUZEduDocValidator();
    private List<Integer> docsWithoutSeria = new ArrayList<Integer>();

    public VUZDocumentTransformer() {
        docsWithoutSeria.add(17);
        docsWithoutSeria.add(18);
        docsWithoutSeria.add(19);
        docsWithoutSeria.add(20);
        docsWithoutSeria.add(37);
    }

    public void valueOf(VUZDocument doc, EduDocDetailsDialogViewModel vm) throws BusinessConditionException, DataValidationException {
        doc.setEduStartDate(vm.getEduStartDate());
        doc.setEduStopDate(vm.getEduStopDate());
        doc.setDocNumber(vuzEduDocValidator.checkEduDocNumber(vm.getDocNumber()));
        doc.setDocIssueDate(vm.getDocIssueDate());
        doc.setDocRegNumber(vm.getDocRegNumber());
        doc.setSpecializationTXT(vm.getSpecialization());
        doc.setQualificationTXT(vm.getQualification());

        EduDocType docType = repositoryService.getEduDocTypeRepository().findOne(vm.getDocTypeID());
        doc.setDocType(docType);

        if (!docsWithoutSeria.contains(doc.getDocType().getID()))
            doc.setDocSeria(vuzEduDocValidator.checkEduDocSeria(vm.getDocSeria()));

        if (("ДИ".equalsIgnoreCase(doc.getDocSeria())) || (docType != null && docType.getName().contains("иностранных")))
            doc.setCitizen(citizenService.createNew(vm.getFirstName(), vm.getSecondName(), vm.getPatronymic(), vm.getMemberOfBel()));
        else if (doc.getCitizen()==null) {
            doc.setCitizen(citizenService.createNew(vm.getFirstName(), vm.getSecondName(), vm.getPatronymic(), vuzEduDocValidator.checkPersonalID(vm.getPersonIdNumber()), vm.getMemberOfBel()));
        } else {
            doc.setCitizen(citizenService.update(doc.getCitizen().getID(), vm.getFirstName(), vm.getSecondName(), vm.getPatronymic(), vuzEduDocValidator.checkPersonalID(doc.getCitizen().getIdNumber()), vm.getMemberOfBel()));
        }

        doc.setEduOrganization(repositoryService.getEduOrganizationRepository().findOne(UUID.fromString(vm.getEduOrg().getValue())));
        doc.setSpecialty(repositoryService.getSpecialtyRepository().findOne(UUID.fromString(vm.getSpecialtyID())));
    }
}
