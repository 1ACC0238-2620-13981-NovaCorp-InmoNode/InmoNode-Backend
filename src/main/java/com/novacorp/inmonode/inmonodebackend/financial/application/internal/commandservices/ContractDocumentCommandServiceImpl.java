package com.novacorp.inmonode.inmonodebackend.financial.application.internal.commandservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.documents.PreliminaryContractComposer;
import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.DocumentRequest;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.commands.RequestContractDocumentCommand;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.DocumentKind;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ReservationRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.ContractDocumentCommandService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.DocumentRequestCommandService;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class ContractDocumentCommandServiceImpl implements ContractDocumentCommandService {

    private final ReservationRepository reservationRepository;
    private final ContractRepository contractRepository;
    private final LotRepository lotRepository;
    private final ProjectRepository projectRepository;
    private final ExternalIamService externalIamService;
    private final DocumentRequestCommandService documents;
    private final Clock clock;

    public ContractDocumentCommandServiceImpl(ReservationRepository reservationRepository,
                                              ContractRepository contractRepository, LotRepository lotRepository,
                                              ProjectRepository projectRepository,
                                              ExternalIamService externalIamService,
                                              DocumentRequestCommandService documents, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.contractRepository = contractRepository;
        this.lotRepository = lotRepository;
        this.projectRepository = projectRepository;
        this.externalIamService = externalIamService;
        this.documents = documents;
        this.clock = clock;
    }

    /** In one transaction, so the request reaches the queue only once it is committed. */
    @Override
    @Transactional
    public Result<DocumentRequest, ApplicationError> handle(RequestContractDocumentCommand command) {
        var requesterId = externalIamService.currentUserId().orElse(null);
        if (requesterId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The requester is not authenticated"));
        }
        return IssuableReservations.find(reservationRepository, contractRepository, command.transactionId())
                .map(reservation -> {
                    var lot = lotRepository.findById(reservation.getLotId()).orElseThrow();
                    var project = projectRepository.findById(lot.getProjectId()).orElseThrow();
                    var buyerEmail = externalIamService.emailOf(reservation.getRequesterId()).orElse(null);
                    var content = PreliminaryContractComposer.compose(reservation, lot, project, buyerEmail,
                            LocalDate.ofInstant(clock.instant(), AccountStatement.SALES_ZONE));
                    return documents.request(DocumentKind.CONTRACT, command.transactionId().toString(), requesterId,
                            content);
                });
    }
}
