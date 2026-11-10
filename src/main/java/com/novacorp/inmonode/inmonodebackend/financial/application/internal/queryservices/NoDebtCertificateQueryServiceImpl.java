package com.novacorp.inmonode.inmonodebackend.financial.application.internal.queryservices;

import com.novacorp.inmonode.inmonodebackend.financial.application.internal.outboundservices.acl.ExternalIamService;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.AccountStatement;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.aggregates.Contract;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.queries.GetNoDebtCertificateQuery;
import com.novacorp.inmonode.inmonodebackend.financial.domain.model.valueobjects.NoDebtCertificate;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.AccountStatementRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ContractRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.LotRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.repositories.ProjectRepository;
import com.novacorp.inmonode.inmonodebackend.financial.domain.services.NoDebtCertificateQueryService;
import com.novacorp.inmonode.inmonodebackend.shared.application.documents.PdfGenerator;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.ApplicationError;
import com.novacorp.inmonode.inmonodebackend.shared.application.result.Result;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class NoDebtCertificateQueryServiceImpl implements NoDebtCertificateQueryService {

    static final String TITLE = "Certificado de No Adeudo";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final AccountStatementRepository accountStatementRepository;
    private final LotRepository lotRepository;
    private final ProjectRepository projectRepository;
    private final ContractRepository contractRepository;
    private final ExternalIamService externalIamService;
    private final PdfGenerator pdfGenerator;
    private final Clock clock;

    public NoDebtCertificateQueryServiceImpl(AccountStatementRepository accountStatementRepository,
                                             LotRepository lotRepository, ProjectRepository projectRepository,
                                             ContractRepository contractRepository,
                                             ExternalIamService externalIamService, PdfGenerator pdfGenerator,
                                             Clock clock) {
        this.accountStatementRepository = accountStatementRepository;
        this.lotRepository = lotRepository;
        this.projectRepository = projectRepository;
        this.contractRepository = contractRepository;
        this.externalIamService = externalIamService;
        this.pdfGenerator = pdfGenerator;
        this.clock = clock;
    }

    @Override
    public Result<byte[], ApplicationError> handle(GetNoDebtCertificateQuery query) {
        var buyerId = externalIamService.currentUserId().orElse(null);
        if (buyerId == null) {
            return Result.failure(new ApplicationError("UNAUTHORIZED", "The buyer is not authenticated"));
        }
        var statement = accountStatementRepository.findById(query.accountStatementId())
                .filter(found -> found.belongsTo(buyerId))
                .orElse(null);
        if (statement == null) {
            return Result.failure(ApplicationError.notFound("account_statement",
                    query.accountStatementId().toString()));
        }
        if (!statement.isFullyPaid()) {
            return Result.failure(ApplicationError.businessRuleViolation("no-debt-certificate",
                    "the account statement still owes exactly %s %s, late fees included"
                            .formatted(statement.balance(), statement.getCurrency())));
        }
        var lot = lotRepository.findById(statement.getLotId()).orElseThrow();
        var project = projectRepository.findById(lot.getProjectId()).orElseThrow();
        var coOwner = contractRepository.findById(statement.getContractId()).map(Contract::getCoOwner).orElse(null);
        var certificate = NoDebtCertificate.issue(statement, project.getName(), lot.getCode(),
                externalIamService.emailOf(buyerId).orElse(null), coOwner, clock.instant());
        return Result.success(pdfGenerator.generateReadOnly(TITLE, lines(certificate)));
    }

    /** In Spanish, as the other documents for the buyers; dates are days in Lima. */
    static List<String> lines(NoDebtCertificate certificate) {
        var currency = certificate.currency();
        var lines = new ArrayList<String>();
        lines.add("Certificado N.° " + certificate.number() + " | Emitido el " + day(certificate.issuedAt()));
        lines.add("");
        lines.add("InmoNode deja constancia de que el comprador indicado no mantiene deuda alguna de capital, "
                + "intereses ni moras por el lote descrito, cuyo estado de cuenta está cancelado en su totalidad. "
                + "Este documento le permite iniciar los trámites de escrituración notarial.");
        lines.add("");
        var user = "usuario N.° " + certificate.buyerId();
        lines.add("Comprador: " + (certificate.buyerEmail() == null ? user
                : certificate.buyerEmail() + " (" + user + ")"));
        var coOwner = certificate.coOwner();
        if (coOwner != null) {
            lines.add("Co-titular: " + coOwner.fullName() + " (" + coOwner.documentType() + " "
                    + coOwner.documentNumber() + ")");
        }
        lines.add("Proyecto: " + certificate.projectName() + " | Lote: " + certificate.lotCode());
        lines.add("Contrato N.° " + certificate.contractId() + " | Estado de cuenta N.° "
                + certificate.accountStatementId());
        lines.add("");
        lines.add("Precio del lote: " + amount(certificate.lotPrice(), currency));
        lines.add("Cuota inicial: " + amount(certificate.initialPayment(), currency));
        lines.add("Cuotas pagadas: %d de %d, por %s".formatted(certificate.installmentCount(),
                certificate.installmentCount(), amount(certificate.installmentsPaid(), currency)));
        lines.add("Moras pagadas: " + amount(certificate.lateFeesPaid(), currency));
        lines.add("Total pagado: " + amount(certificate.totalPaid(), currency));
        lines.add("Saldo pendiente: " + amount(BigDecimal.ZERO.setScale(2), currency));
        lines.add("Fecha de cancelación: " + day(certificate.paidOffAt()));
        lines.add("");
        lines.add("Firma digital representativa");
        lines.add("Firmado digitalmente por InmoNode — Control Financiero y Documental");
        lines.add("Código de verificación: " + certificate.verificationCode());
        return lines;
    }

    private static String amount(BigDecimal value, String currency) {
        return value.toPlainString() + " " + currency;
    }

    private static String day(Instant instant) {
        return LocalDate.ofInstant(instant, AccountStatement.SALES_ZONE).format(DATE);
    }
}
