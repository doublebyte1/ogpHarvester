/*
 * IngestFormBean.java
 *
 * Copyright (C) 2013
 *
 * This file is part of Open Geoportal Harvester.
 *
 * This software is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any
 * later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this library; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 *
 * As a special exception, if you link this library with other files to produce
 * an executable, this library does not by itself cause the resulting executable
 * to be covered by the GNU General Public License. This exception does not
 * however invalidate any other reasons why the executable file might be covered
 * by the GNU General Public License.
 *
 * Authors:: Jose García (mailto:jose.garcia@geocat.net)
 */
package org.opengeoportal.harvester.api.dao;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengeoportal.harvester.api.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:spring/test-data-config.xml"})
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
        DbUnitTestExecutionListener.class,
        DirtiesContextTestExecutionListener.class,
        TransactionalTestExecutionListener.class})
@Transactional
public class IngestRepositoryTest {
    @Autowired
    private IngestRepository ingestRepository;

    @Test
    @DatabaseSetup("ingestData.xml")
    public void testFindIngest() {

        Ingest ingest = ingestRepository.findOne(1L);
        Assert.assertNotNull(ingest);
        Assert.assertEquals("ingest1", ingest.getName());
    }

    @Test
    @DatabaseSetup("ingestData.xml")
    public void testDeleteIngest() {
        ingestRepository.delete(1L);

        List<Ingest> ingests = ingestRepository.findAll();
        Assert.assertEquals(1, ingests.size());
    }

    @Test
    @DatabaseSetup("ingestData.xml")
    public void testUpdateIngest() {

        Ingest ingest = ingestRepository.findByName("ingest2");
        ingest.setName("ingest2 changed");
        Ingest ingestSaved = ingestRepository.save(ingest);

        Assert.assertEquals("ingest2 changed", ingestSaved.getName());
    }

    @Test
    public void testInsertIngest() {
        Ingest ingest = new IngestOGP();
        ingest.setName("ingest3");
        ingest.setUrl("http://ingest3");
        ingest.setBeginDate(new Date());
        ingest.setFrequency(Frequency.DAILY);
        ingest.addRequiredField("themeKeyword");

        Ingest ingestCreated = ingestRepository.save(ingest);

        Assert.assertNotNull(ingestCreated);

        Ingest ingestRetrieved = ingestRepository.findByName("ingest3");

        Assert.assertEquals(ingestCreated, ingestRetrieved);
    }

    @Test
    @DatabaseSetup("ingestData.xml")
    public void testUpdateIngestWithJob() {
        Ingest ingest = ingestRepository.findByName("ingest2");

        IngestJobStatus job = new IngestJobStatus();
        job.setStatus(IngestJobStatusValue.SUCCESSED);
        job.setStartTime(new Date());
        job.setEndTime(new Date());

        IngestReport report = new IngestReport();
        report.setPublicRecords(100);
        report.setRestrictedRecords(20);
        report.setRasterRecords(70);
        report.setVectorRecords(50);

        report.setWebServiceWarnings(20);
        report.setUnrequiredFieldWarnings(60);

        IngestReportError reportError = new IngestReportError();
        reportError.setField("title");
        reportError.setType(IngestReportErrorType.REQUIRED_FIELD_ERROR);
        report.addError(reportError);

        job.setIngestReport(report);

        ingest.addJobStatus(job);

        Ingest ingestUpdated = ingestRepository.save(ingest);

        Assert.assertNotNull(ingestUpdated);

        Ingest ingestRetrieved = ingestRepository.findByName("ingest2");
        Assert.assertEquals(ingestUpdated, ingestRetrieved);

        Assert.assertEquals(1, ingestRetrieved.getIngestJobStatuses().size());

        IngestReport reportRetrieved = ingestRetrieved.getIngestJobStatuses().get(0).getIngestReport();
        Assert.assertEquals(ingestUpdated.getIngestJobStatuses().get(0).getIngestReport(), reportRetrieved);

        Assert.assertEquals(IngestJobStatusValue.SUCCESSED, ingestRetrieved.getIngestJobStatuses().get(0).getStatus());
        Assert.assertEquals(100, reportRetrieved.getPublicRecords());
        Assert.assertEquals(20, reportRetrieved.getRestrictedRecords());
    }


}
