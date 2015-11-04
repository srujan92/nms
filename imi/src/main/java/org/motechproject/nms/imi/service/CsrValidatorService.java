package org.motechproject.nms.imi.service;

import org.motechproject.nms.kilkari.dto.CallSummaryRecordDto;
import org.motechproject.nms.imi.domain.CallSummaryRecord;

public interface CsrValidatorService {
    /**
     * Clears the memory used by the cache
     */
    void evictCache();

    /**
     * Validates a call summary record. Throws InvalidCallSummaryRecord exception if something goes wrong.

     * NOTE: directly used in IT only
     *
     * @param record
     */
    void validateSummaryRecordDto(CallSummaryRecordDto record);

    /**
     * Validates a call summary record. Throws InvalidCallSummaryRecord exception if something goes wrong.
     *
     * @param record
     */
    void validateSummaryRecord(CallSummaryRecord record);
}
