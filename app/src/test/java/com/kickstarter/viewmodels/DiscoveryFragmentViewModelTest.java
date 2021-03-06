package com.kickstarter.viewmodels;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.kickstarter.KSRobolectricTestCase;
import com.kickstarter.factories.ActivityEnvelopeFactory;
import com.kickstarter.factories.ActivityFactory;
import com.kickstarter.factories.CategoryFactory;
import com.kickstarter.factories.DiscoverEnvelopeFactory;
import com.kickstarter.factories.ProjectFactory;
import com.kickstarter.factories.UserFactory;
import com.kickstarter.libs.CurrentUserType;
import com.kickstarter.libs.Environment;
import com.kickstarter.libs.KoalaEvent;
import com.kickstarter.libs.MockCurrentUser;
import com.kickstarter.libs.RefTag;
import com.kickstarter.libs.preferences.MockIntPreference;
import com.kickstarter.libs.utils.ListUtils;
import com.kickstarter.models.Activity;
import com.kickstarter.models.Project;
import com.kickstarter.services.ApiClientType;
import com.kickstarter.services.DiscoveryParams;
import com.kickstarter.services.MockApiClient;
import com.kickstarter.services.apiresponses.ActivityEnvelope;
import com.kickstarter.services.apiresponses.DiscoverEnvelope;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Observable;
import rx.observers.TestSubscriber;

public class DiscoveryFragmentViewModelTest extends KSRobolectricTestCase {
  private DiscoveryFragmentViewModel.ViewModel vm;

  private final TestSubscriber<Activity> activityTest = new TestSubscriber<>();
  private final TestSubscriber<Boolean> hasProjects = new TestSubscriber<>();
  private final TestSubscriber<List<Project>> projects = new TestSubscriber<>();
  private final TestSubscriber<Boolean> shouldShowEmptySavedView = new TestSubscriber<>();
  private final TestSubscriber<Boolean> shouldShowOnboardingViewTest = new TestSubscriber<>();
  private final TestSubscriber<Boolean> showActivityFeed = new TestSubscriber<>();
  private final TestSubscriber<Boolean> showLoginTout = new TestSubscriber<>();
  private final TestSubscriber<Pair<Project, RefTag>> showProject = new TestSubscriber<>();
  private final TestSubscriber<Activity> startUpdateActivity = new TestSubscriber<>();

  private void setUpEnvironment(final @NonNull Environment environment) {
    this.vm = new DiscoveryFragmentViewModel.ViewModel(environment);
    this.vm.outputs.activity().subscribe(this.activityTest);
    this.vm.outputs.projectList().map(ListUtils::nonEmpty).subscribe(this.hasProjects);
    this.vm.outputs.projectList().filter(ListUtils::nonEmpty).subscribe(this.projects);
    this.vm.outputs.shouldShowEmptySavedView().subscribe(this.shouldShowEmptySavedView);
    this.vm.outputs.shouldShowOnboardingView().subscribe(this.shouldShowOnboardingViewTest);
    this.vm.outputs.showActivityFeed().subscribe(this.showActivityFeed);
    this.vm.outputs.showLoginTout().subscribe(this.showLoginTout);
    this.vm.outputs.startProjectActivity().subscribe(this.showProject);
    this.vm.outputs.startUpdateActivity().subscribe(this.startUpdateActivity);
  }

  private void setUpInitialHomeAllProjectsParams() {
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().sort(DiscoveryParams.Sort.HOME).build());
    this.vm.inputs.rootCategories(CategoryFactory.rootCategories());
  }

  @Test
  public void testProjectsEmitWithNewCategoryParams() {
    setUpEnvironment(environment());

    // Load initial params and root categories from activity.
    setUpInitialHomeAllProjectsParams();

    // Should emit current fragment's projects.
    this.hasProjects.assertValues(true);
    this.koalaTest.assertValues("Discover List View");

    // Select a new category.
    this.vm.inputs.paramsFromActivity(
      DiscoveryParams.builder()
        .category(CategoryFactory.artCategory())
        .sort(DiscoveryParams.Sort.HOME)
        .build()
    );

    // Projects are cleared, new projects load.
    this.hasProjects.assertValues(true, false, true);
    this.koalaTest.assertValues("Discover List View", "Discover List View");

    this.vm.inputs.clearPage();
    this.hasProjects.assertValues(true, false, true, false);
  }

  @Test
  public void testProjectsEmitWithNewSort() {
    setUpEnvironment(environment());

    // Initial load.
    setUpInitialHomeAllProjectsParams();

    this.projects.assertValueCount(1);
    this.koalaTest.assertValues("Discover List View");

    // Popular tab clicked.
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().sort(DiscoveryParams.Sort.POPULAR).build());
    this.projects.assertValueCount(2);
    this.koalaTest.assertValues("Discover List View", "Discover List View");
  }

  @Test
  public void testProjectsRefreshAfterLogin() {
    final CurrentUserType currentUser = new MockCurrentUser();

    final Environment environment = environment().toBuilder()
      .currentUser(currentUser)
      .build();

    setUpEnvironment(environment);
    
    // Initial load.
    setUpInitialHomeAllProjectsParams();

    this.hasProjects.assertValue(true);

    // Projects should emit.
    this.projects.assertValueCount(1);

    // Log in.
    currentUser.refresh(UserFactory.user());

    // Projects should emit again.
    this.projects.assertValueCount(2);
  }

  @Test
  public void testShouldShowEmptySavedView_isFalse_whenUserHasSavedProjects() {
    final CurrentUserType currentUser = new MockCurrentUser();

    final Environment environment = environment().toBuilder()
      .apiClient(new MockApiClient())
      .currentUser(currentUser)
      .build();

    setUpEnvironment(environment);

    // Initial home all projects params.
    setUpInitialHomeAllProjectsParams();

    this.hasProjects.assertValue(true);
    this.shouldShowEmptySavedView.assertValue(false);

    // Login.
    currentUser.refresh(UserFactory.user());

    // Projects are cleared, new projects load.
    this.hasProjects.assertValues(true, false, true);
    this.shouldShowEmptySavedView.assertValues(false);

    // Saved projects params.
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().starred(1).build());

    // Projects are cleared, new projects load.
    this.hasProjects.assertValues(true, false, true, false, true);
    this.shouldShowEmptySavedView.assertValues(false);
  }

  @Test
  public void testShouldShowEmptySavedView_isTrue_whenUserHasNoSavedProjects() {
    final CurrentUserType currentUser = new MockCurrentUser();
    final ApiClientType apiClient = new MockApiClient() {
      @Override
      public @NonNull Observable<DiscoverEnvelope> fetchProjects(final @NonNull DiscoveryParams params) {
        if (params.isSavedProjects()) {
          return Observable.just(DiscoverEnvelopeFactory.discoverEnvelope(new ArrayList<>()));
        } else {
          return super.fetchProjects(params);
        }
      }
    };

    final Environment environment = environment().toBuilder()
      .apiClient(apiClient)
      .currentUser(currentUser)
      .build();

    setUpEnvironment(environment);

    // Initial home all projects params.
    setUpInitialHomeAllProjectsParams();

    this.hasProjects.assertValue(true);
    this.shouldShowEmptySavedView.assertValue(false);

    // Login.
    currentUser.refresh(UserFactory.user());

    // Projects are cleared, new projects load.
    this.hasProjects.assertValues(true, false, true);
    this.shouldShowEmptySavedView.assertValues(false);

    // Saved projects params.
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().starred(1).build());

    // Projects are cleared, new projects load.
    this.hasProjects.assertValues(true, false, true, false, false);
    this.shouldShowEmptySavedView.assertValues(false, true);
  }

  @Test
  public void testShowHeaderViews() {
    final CurrentUserType currentUser = new MockCurrentUser();
    final Activity activity = ActivityFactory.activity();
    final ApiClientType apiClient = new MockApiClient() {
      @Override
      public @NonNull Observable<ActivityEnvelope> fetchActivities() {
        return Observable.just(
          ActivityEnvelopeFactory.activityEnvelope(Collections.singletonList(activity))
        );
      }
    };
    final MockIntPreference activitySamplePreference = new MockIntPreference(987654321);

    final Environment environment = environment().toBuilder()
      .activitySamplePreference(activitySamplePreference)
      .apiClient(apiClient)
      .currentUser(currentUser)
      .build();

    setUpEnvironment(environment);

    // Initial home all projects params.
    setUpInitialHomeAllProjectsParams();

    // Should show onboarding view.
    this.shouldShowOnboardingViewTest.assertValues(true);
    this.activityTest.assertValue(null);

    // Change params. Onboarding view should not be shown.
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().build());
    this.shouldShowOnboardingViewTest.assertValues(true, false);
    this.activityTest.assertValues(null, null);

    // Login.
    currentUser.refresh(UserFactory.user());

    // Activity sampler should be shown rather than onboarding view.
    this.shouldShowOnboardingViewTest.assertValues(true, false, false);
    this.activityTest.assertValues(null, null, activity);

    // Change params. Activity sampler should not be shown.
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().build());
    this.activityTest.assertValues(null, null, activity, null);
  }

  @Test
  public void testClickingInterfaceElements() {
    setUpEnvironment(environment());

    // Clicking see activity feed button on sampler should show activity feed.
    this.showActivityFeed.assertNoValues();
    this.vm.inputs.activitySampleFriendBackingViewHolderSeeActivityClicked(null);
    this.showActivityFeed.assertValues(true);
    this.vm.inputs.activitySampleFriendFollowViewHolderSeeActivityClicked(null);
    this.showActivityFeed.assertValues(true, true);
    this.vm.inputs.activitySampleProjectViewHolderSeeActivityClicked(null);
    this.showActivityFeed.assertValues(true, true, true);

    // Clicking activity update on sampler should show activity update.
    this.startUpdateActivity.assertNoValues();
    this.vm.inputs.activitySampleProjectViewHolderUpdateClicked(null, ActivityFactory.updateActivity());
    this.startUpdateActivity.assertValueCount(1);
    this.koalaTest.assertValues(KoalaEvent.VIEWED_UPDATE);

    // Clicking login on onboarding view should show login tout.
    this.showLoginTout.assertNoValues();
    this.vm.inputs.discoveryOnboardingViewHolderLoginToutClick(null);
    this.showLoginTout.assertValue(true);

    // Pass in params and sort to fetch projects.
    this.vm.inputs.paramsFromActivity(DiscoveryParams.builder().build());

    // Clicking on a project card should show project activity.
    this.showProject.assertNoValues();
    this.vm.inputs.projectCardViewHolderClicked(ProjectFactory.project());
    this.showProject.assertValueCount(1);
  }
}
