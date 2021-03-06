/*
 * Copyright 2016-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.net.intent.impl.compiler;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.VlanId;
import org.onlab.util.Bandwidth;
import org.onosproject.TestApplicationId;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.AbstractIntentTest;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentException;
import org.onosproject.net.intent.IntentTestsMocks;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.LinkCollectionIntent;
import org.onosproject.net.intent.SinglePointToMultiPointIntent;
import org.onosproject.net.intent.constraint.BandwidthConstraint;
import org.onosproject.net.intent.constraint.PartialFailureConstraint;
import org.onosproject.net.resource.ContinuousResource;
import org.onosproject.net.resource.MockResourceService;
import org.onosproject.net.resource.ResourceAllocation;
import org.onosproject.net.resource.ResourceService;
import org.onosproject.net.resource.Resources;
import org.onosproject.net.topology.PathService;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.onosproject.net.intent.LinksHaveEntryWithSourceDestinationPairMatcher.linksHasPath;

/**
 * Unit tests for SinglePointToMultiPointIntentCompiler.
 */
public class SinglePointToMultiPointIntentCompilerTest extends AbstractIntentTest {

    private static final ApplicationId APPID = new TestApplicationId("foo");
    private static final String S1 = "s1";
    private static final String S2 = "s2";
    private static final String S3 = "s3";
    private static final String S4 = "s4";
    private static final String S5 = "s5";
    private static final String S6 = "s6";
    private static final String S7 = "s7";
    private static final String S8 = "s8";

    private static final DeviceId DID_1 = DeviceId.deviceId("of:" + S1);
    private static final DeviceId DID_2 = DeviceId.deviceId("of:" + S2);
    private static final DeviceId DID_3 = DeviceId.deviceId("of:" + S3);
    private static final DeviceId DID_4 = DeviceId.deviceId("of:" + S4);
    private static final DeviceId DID_5 = DeviceId.deviceId("of:" + S5);
    private static final DeviceId DID_8 = DeviceId.deviceId("of:" + S8);

    private static final PortNumber PORT_1 = PortNumber.portNumber(1);
    private static final PortNumber PORT_2 =  PortNumber.portNumber(2);
    private static final PortNumber PORT_3 =  PortNumber.portNumber(3);

    private TrafficSelector selector = new IntentTestsMocks.MockSelector();
    private TrafficTreatment treatment = new IntentTestsMocks.MockTreatment();

    /**
     * Creates a SinglePointToMultiPoint intent for an ingress point
     * and a group of egress points.
     *
     * @param ingress the filtered ingress point
     * @param egress the set of filtered egress points
     * @return a single point to multi point intent
     */
    private SinglePointToMultiPointIntent makeIntent(FilteredConnectPoint ingress,
                                                     Set<FilteredConnectPoint> egress) {
        return makeIntent(ingress, egress, selector);
    }

    /**
     * Generates SinglePointToMultiPointIntent with filtered connection point
     * and the specified selector.
     *
     * @param ingress the filtered ingress point
     * @param egress the filtered egress point
     * @param trafficSelector the intent traffic selector
     * @return a single point to multi point intent
     */
    private SinglePointToMultiPointIntent makeIntent(FilteredConnectPoint ingress,
                                                     Set<FilteredConnectPoint> egress,
                                                     TrafficSelector trafficSelector) {
        return makeIntent(ingress, egress, trafficSelector, Lists.newArrayList());
    }

    /**
     * Generates SinglePointToMultiPointIntent with filtered connection point
     * and the given constraints set.
     *
     * @param ingress the filtered ingress point
     * @param egress the filtered egress point
     * @param constraints the list of intent constraints
     * @return a single point to multi point intent
     */
    private SinglePointToMultiPointIntent makeIntent(FilteredConnectPoint ingress,
                                                     Set<FilteredConnectPoint> egress,
                                                     List<Constraint> constraints) {
        return makeIntent(ingress, egress, selector, constraints);
    }

    /**
     * Generates SinglePointToMultiPointIntent with filtered connection point,
     * and the specified traffic selector and intent constraints.
     *
     * @param ingress the filtered ingress point
     * @param egress the set of filtered egress points
     * @param trafficSelector the intent traffic selector
     * @param constraints the list of intent constraints
     * @return a single point to multi point intent
     */
    private SinglePointToMultiPointIntent makeIntent(FilteredConnectPoint ingress,
                                                     Set<FilteredConnectPoint> egress,
                                                     TrafficSelector trafficSelector,
                                                     List<Constraint> constraints) {
        return SinglePointToMultiPointIntent.builder()
                .appId(APPID)
                .treatment(treatment)
                .selector(trafficSelector)
                .filteredIngressPoint(ingress)
                .filteredEgressPoints(egress)
                .constraints(constraints)
                .build();
    }

    /**
     * Creates a compiler for SinglePointToMultiPoint intents.
     *
     * @param hops hops to use while computing paths for this intent
     * @param pathService the path service
     * @param resourceService the resource service
     * @return a single point to multi point intent compiler
     */
    private SinglePointToMultiPointIntentCompiler makeCompiler(String[] hops,
                                                               PathService pathService,
                                                               ResourceService resourceService) {
        SinglePointToMultiPointIntentCompiler compiler =
                new SinglePointToMultiPointIntentCompiler();
        compiler.deviceService = new IntentTestsMocks.MockDeviceService();

        if (pathService == null) {
            compiler.pathService = new IntentTestsMocks.Mp2MpMockPathService(hops);
        } else {
            compiler.pathService = pathService;
        }

        if (resourceService == null) {
            compiler.resourceService = new MockResourceService();
        } else {
            compiler.resourceService = resourceService;
        }

        return compiler;
    }

    /**
     * Creates a compiler for SinglePointToMultiPoint intents.
     *
     * @param hops the hops to use while computing paths for this intent
     * @return a single point to multi point intent compiler
     */
    private SinglePointToMultiPointIntentCompiler makeCompiler(String[] hops) {
        return makeCompiler(hops, null, null);
    }

    /**
     * Tests a single ingress point with 8 hops to its egress point.
     */
    @Test
    public void testSingleLongPathCompilation() {
        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));
        Set<FilteredConnectPoint> egress =
                Sets.newHashSet(new FilteredConnectPoint(new ConnectPoint(DID_8, PORT_2)));

        SinglePointToMultiPointIntent intent =
                makeIntent(ingress, egress);

        assertThat(intent, is(notNullValue()));

        String[] hops = {S2, S3, S4, S5, S6, S7};
        SinglePointToMultiPointIntentCompiler compiler = makeCompiler(hops);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(Matchers.notNullValue()));
        assertThat(result, hasSize(1));
        Intent resultIntent = result.get(0);
        assertThat(resultIntent instanceof LinkCollectionIntent, is(true));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(7));
            assertThat(linkIntent.links(), linksHasPath(S1, S2));
            assertThat(linkIntent.links(), linksHasPath(S2, S3));
            assertThat(linkIntent.links(), linksHasPath(S3, S4));
            assertThat(linkIntent.links(), linksHasPath(S4, S5));
            assertThat(linkIntent.links(), linksHasPath(S5, S6));
            assertThat(linkIntent.links(), linksHasPath(S6, S7));
            assertThat(linkIntent.links(), linksHasPath(S7, S8));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));
    }

    /**
     * Tests a simple topology where two egress points share some path segments
     * and some path segments are not shared.
     */
    @Test
    public void testTwoEgressCompilation() {
        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        FilteredConnectPoint egressOne =
                new FilteredConnectPoint(new ConnectPoint(DID_4, PORT_2));
        FilteredConnectPoint egressTwo =
                new FilteredConnectPoint(new ConnectPoint(DID_5, PORT_2));
        Set<FilteredConnectPoint> egress = Sets.newHashSet(egressOne, egressTwo);

        SinglePointToMultiPointIntent intent = makeIntent(ingress, egress);

        assertThat(intent, is(notNullValue()));

        final String[] hops = {S2, S3};
        SinglePointToMultiPointIntentCompiler compiler = makeCompiler(hops);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(notNullValue()));
        assertThat(result, hasSize(1));
        Intent resultIntent = result.get(0);
        assertThat(resultIntent instanceof LinkCollectionIntent, is(true));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(4));
            assertThat(linkIntent.links(), linksHasPath(S1, S2));
            assertThat(linkIntent.links(), linksHasPath(S2, S3));
            assertThat(linkIntent.links(), linksHasPath(S3, S4));
            assertThat(linkIntent.links(), linksHasPath(S3, S5));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));
    }

    /**
     * Tests multiple egress points that share a common path to the ingress
     * point.
     */
    @Test
    public void testMultiEgressCompilation() {
        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        FilteredConnectPoint egressOne =
                new FilteredConnectPoint(new ConnectPoint(DID_3, PORT_2));
        FilteredConnectPoint egressTwo =
                new FilteredConnectPoint(new ConnectPoint(DID_4, PORT_2));
        FilteredConnectPoint egressThree =
                new FilteredConnectPoint(new ConnectPoint(DID_5, PORT_2));
        Set<FilteredConnectPoint> egress = Sets.newHashSet(egressOne,
                                                           egressTwo,
                                                           egressThree);

        SinglePointToMultiPointIntent intent = makeIntent(ingress, egress);

        assertThat(intent, is(notNullValue()));

        final String[] hops = {S2};
        SinglePointToMultiPointIntentCompiler compiler = makeCompiler(hops);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(notNullValue()));
        assertThat(result, hasSize(1));
        Intent resultIntent = result.get(0);
        assertThat(resultIntent instanceof LinkCollectionIntent, is(true));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(4));
            assertThat(linkIntent.links(), linksHasPath(S1, S2));
            assertThat(linkIntent.links(), linksHasPath(S2, S3));
            assertThat(linkIntent.links(), linksHasPath(S2, S4));
            assertThat(linkIntent.links(), linksHasPath(S2, S5));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));
    }

    /**
     * Tests ingress and egress on the same device.
     */
    @Test
    public void testSameDeviceCompilation() {
        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        Set<FilteredConnectPoint> egress =
                Sets.newHashSet(new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_2)),
                                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_3)));

        SinglePointToMultiPointIntent intent = makeIntent(ingress, egress);
        assertThat(intent, is(notNullValue()));

        final String[] hops = {};
        SinglePointToMultiPointIntentCompiler compiler = makeCompiler(hops);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(notNullValue()));
        assertThat(result, hasSize(1));
        Intent resultIntent = result.get(0);
        assertThat(resultIntent, instanceOf(LinkCollectionIntent.class));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(0));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));
    }

    /**
     * Tests filtered ingress and egress connect points.
     */
    @Test
    public void testFilteredConnectPointIntent() {

        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        Set<FilteredConnectPoint> egress = ImmutableSet.of(
                new FilteredConnectPoint(new ConnectPoint(DID_3, PORT_1),
                                         DefaultTrafficSelector.builder().matchVlanId(VlanId.vlanId("100")).build()),
                new FilteredConnectPoint(new ConnectPoint(DID_4,  PORT_1),
                                         DefaultTrafficSelector.builder().matchVlanId(VlanId.vlanId("200")).build())
        );

        SinglePointToMultiPointIntent intent = makeIntent(ingress, egress, selector);
        String[] hops = {S2};

        SinglePointToMultiPointIntentCompiler compiler = makeCompiler(hops);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(notNullValue()));
        assertThat(result, hasSize(1));

        Intent resultIntent = result.get(0);
        assertThat(resultIntent, instanceOf(LinkCollectionIntent.class));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(3));

            assertThat(linkIntent.links(), linksHasPath(S1, S2));
            assertThat(linkIntent.links(), linksHasPath(S2, S3));
            assertThat(linkIntent.links(), linksHasPath(S2, S4));

            Set<FilteredConnectPoint> ingressPoints = linkIntent.filteredIngressPoints();
            assertThat("Link collection ingress points do not match base intent",
                       ingressPoints.size() == 1 && ingressPoints.contains(intent.filteredIngressPoint()));

            assertThat("Link collection egress points do not match base intent",
                       linkIntent.filteredEgressPoints().equals(intent.filteredEgressPoints()));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));

    }

    /**
     * Tests filtered ingress and egress points with an intent selector set.
     */
    @Test
    public void testNonTrivialSelectorsIntent() {

        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        Set<FilteredConnectPoint> egress = ImmutableSet.of(
                new FilteredConnectPoint(new ConnectPoint(DID_3, PORT_1),
                                         DefaultTrafficSelector.builder().matchVlanId(VlanId.vlanId("100")).build()),
                new FilteredConnectPoint(new ConnectPoint(DID_4, PORT_1),
                                         DefaultTrafficSelector.builder().matchVlanId(VlanId.vlanId("200")).build())
        );

        TrafficSelector ipPrefixSelector = DefaultTrafficSelector.builder()
                .matchIPDst(IpPrefix.valueOf("192.168.100.0/24"))
                .build();

        SinglePointToMultiPointIntent intent = makeIntent(ingress, egress, ipPrefixSelector);
        String[] hops = {S2};

        SinglePointToMultiPointIntentCompiler compiler = makeCompiler(hops);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(notNullValue()));
        assertThat(result, hasSize(1));

        Intent resultIntent = result.get(0);
        assertThat(resultIntent, instanceOf(LinkCollectionIntent.class));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(3));

            assertThat(linkIntent.links(), linksHasPath(S1, S2));
            assertThat(linkIntent.links(), linksHasPath(S2, S3));
            assertThat(linkIntent.links(), linksHasPath(S2, S4));

            Set<FilteredConnectPoint> ingressPoints = linkIntent.filteredIngressPoints();
            assertThat("Link collection ingress points do not match base intent",
                       ingressPoints.size() == 1 && ingressPoints.contains(intent.filteredIngressPoint()));

            assertThat("Link collection egress points do not match base intent",
                       linkIntent.filteredEgressPoints().equals(intent.filteredEgressPoints()));
            assertThat(linkIntent.selector(), is(ipPrefixSelector));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));
    }

    /**
     * Tests if bandwidth resources get allocated correctly.
     */
    @Test
    public void testBandwidthConstrainedIntentAllocation() {
        final double bpsTotal = 1000.0;
        final double bpsToReserve = 100.0;

        ContinuousResource resourceSw1P1 =
                Resources.continuous(DID_1, PORT_1, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw1P2 =
                Resources.continuous(DID_1, PORT_2, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw2P1 =
                Resources.continuous(DID_2, PORT_1, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw2P2 =
                Resources.continuous(DID_2, PORT_2, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw3P1 =
                Resources.continuous(DID_3, PORT_1, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw3P2 =
                Resources.continuous(DID_3, PORT_2, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw3P3 =
                Resources.continuous(DID_3, PORT_3, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw4P1 =
                Resources.continuous(DID_4, PORT_1, Bandwidth.class)
                        .resource(bpsToReserve);
        ContinuousResource resourceSw4P2 =
                Resources.continuous(DID_4, PORT_2, Bandwidth.class)
                        .resource(bpsToReserve);

        String[] hops = {DID_3.toString()};

        final ResourceService resourceService =
                MockResourceService.makeCustomBandwidthResourceService(bpsTotal);
        final List<Constraint> constraints =
                Collections.singletonList(new BandwidthConstraint(Bandwidth.bps(bpsToReserve)));

        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_4, PORT_1));

        Set<FilteredConnectPoint> egress = ImmutableSet.of(
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_2)),
                new FilteredConnectPoint(new ConnectPoint(DID_2, PORT_2)));

        TrafficSelector ipPrefixSelector = DefaultTrafficSelector.builder()
                .matchIPDst(IpPrefix.valueOf("192.168.100.0/24"))
                .build();

        SinglePointToMultiPointIntent intent =
                makeIntent(ingress, egress, ipPrefixSelector, constraints);

        SinglePointToMultiPointIntentCompiler compiler =
                makeCompiler(null,
                             new IntentTestsMocks.FixedMP2MPMockPathService(hops),
                             resourceService);

        compiler.compile(intent, null);

        Key intentKey = intent.key();

        ResourceAllocation rA1 = new ResourceAllocation(resourceSw1P1, intentKey);
        ResourceAllocation rA2 = new ResourceAllocation(resourceSw1P2, intentKey);
        ResourceAllocation rA3 = new ResourceAllocation(resourceSw2P1, intentKey);
        ResourceAllocation rA4 = new ResourceAllocation(resourceSw2P2, intentKey);
        ResourceAllocation rA5 = new ResourceAllocation(resourceSw3P1, intentKey);
        ResourceAllocation rA6 = new ResourceAllocation(resourceSw3P2, intentKey);
        ResourceAllocation rA7 = new ResourceAllocation(resourceSw3P3, intentKey);
        ResourceAllocation rA8 = new ResourceAllocation(resourceSw4P1, intentKey);
        ResourceAllocation rA9 = new ResourceAllocation(resourceSw4P2, intentKey);

        Set<ResourceAllocation> expectedResourceAllocations =
                ImmutableSet.of(rA1, rA2, rA3, rA4, rA5, rA6, rA7, rA8, rA9);

        Set<ResourceAllocation> resourceAllocations =
                ImmutableSet.copyOf(resourceService.getResourceAllocations(intentKey));

        assertThat(resourceAllocations, hasSize(9));
        assertEquals(expectedResourceAllocations, resourceAllocations);
    }

    /**
     * Tests if all expected links are present when a partial failure
     * constraint is used and one ingress is not present.
     */
    @Test
    public void testPartialFailureConstraintSuccess() {
        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        Set<FilteredConnectPoint> egress = ImmutableSet.of(
                new FilteredConnectPoint(new ConnectPoint(DID_4, PORT_2)),
                new FilteredConnectPoint(new ConnectPoint(DID_5, PORT_2)));

        final List<Constraint> constraints =
                Collections.singletonList(new PartialFailureConstraint());

        SinglePointToMultiPointIntent intent =
                makeIntent(ingress, egress, constraints);

        String[] hops = {S3};

        SinglePointToMultiPointIntentCompiler compiler =
                makeCompiler(null,
                             new IntentTestsMocks.FixedMP2MPMockPathService(hops),
                             null);
        assertThat(compiler, is(notNullValue()));

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, is(notNullValue()));
        assertThat(result, hasSize(1));

        Intent resultIntent = result.get(0);
        assertThat(resultIntent, instanceOf(LinkCollectionIntent.class));

        if (resultIntent instanceof LinkCollectionIntent) {
            LinkCollectionIntent linkIntent = (LinkCollectionIntent) resultIntent;
            assertThat(linkIntent.links(), hasSize(2));
            assertThat(linkIntent.links(), linksHasPath(S1, S3));
            assertThat(linkIntent.links(), linksHasPath(S3, S4));
        }
        assertThat("key is inherited", resultIntent.key(), is(intent.key()));
    }

    /**
     * Exception expected to be raised when an intent does not find all paths
     * and a partiale failure constraint is not specified.
     */
    @Rule
    public ExpectedException intentException = ExpectedException.none();

    /**
     * Tests if compiling an intent without partial failure constraints set and
     * with a missing egress connect point generates an exception and no other
     * results.
     */
    @Test
    public void testPartialFailureConstraintFailure() {
        FilteredConnectPoint ingress =
                new FilteredConnectPoint(new ConnectPoint(DID_1, PORT_1));

        Set<FilteredConnectPoint> egress = ImmutableSet.of(
                new FilteredConnectPoint(new ConnectPoint(DID_4, PORT_2)),
                new FilteredConnectPoint(new ConnectPoint(DID_5, PORT_2)));

        SinglePointToMultiPointIntent intent =
                makeIntent(ingress, egress);

        String[] hops = {S3};

        SinglePointToMultiPointIntentCompiler compiler =
                makeCompiler(null,
                             new IntentTestsMocks.FixedMP2MPMockPathService(hops),
                             null);
        assertThat(compiler, is(notNullValue()));

        intentException.expect(IntentException.class);

        List<Intent> result = compiler.compile(intent, null);
        assertThat(result, null);
    }
}
