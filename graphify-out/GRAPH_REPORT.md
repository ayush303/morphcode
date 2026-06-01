# Graph Report - .  (2026-06-01)

## Corpus Check
- Corpus is ~19,278 words - fits in a single context window. You may not need a graph.

## Summary
- 770 nodes · 1285 edges · 68 communities (59 shown, 9 thin omitted)
- Extraction: 90% EXTRACTED · 10% INFERRED · 0% AMBIGUOUS · INFERRED: 134 edges (avg confidence: 0.82)
- Token cost: 2,800 input · 1,950 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Spring Security Config|Spring Security Config]]
- [[_COMMUNITY_AI Chat & Config|AI Chat & Config]]
- [[_COMMUNITY_Subscription & Roles|Subscription & Roles]]
- [[_COMMUNITY_Storage & MinIO|Storage & MinIO]]
- [[_COMMUNITY_Project Service|Project Service]]
- [[_COMMUNITY_Stripe Payment|Stripe Payment]]
- [[_COMMUNITY_Auth API|Auth API]]
- [[_COMMUNITY_Project Members|Project Members]]
- [[_COMMUNITY_Chat API|Chat API]]
- [[_COMMUNITY_LLM File Advisor|LLM File Advisor]]
- [[_COMMUNITY_Usage Tracking|Usage Tracking]]
- [[_COMMUNITY_Error Handling|Error Handling]]
- [[_COMMUNITY_Billing API|Billing API]]
- [[_COMMUNITY_AI Architecture|AI Architecture]]
- [[_COMMUNITY_Chat Event Parsing|Chat Event Parsing]]
- [[_COMMUNITY_Project Permissions|Project Permissions]]
- [[_COMMUNITY_Project REST Endpoints|Project REST Endpoints]]
- [[_COMMUNITY_Domain Enums & Entities|Domain Enums & Entities]]
- [[_COMMUNITY_App Startup & Beans|App Startup & Beans]]
- [[_COMMUNITY_Member REST Endpoints|Member REST Endpoints]]
- [[_COMMUNITY_Project Service Interface|Project Service Interface]]
- [[_COMMUNITY_File Management API|File Management API]]
- [[_COMMUNITY_Auth Endpoint Definitions|Auth Endpoint Definitions]]
- [[_COMMUNITY_Member DTOs|Member DTOs]]
- [[_COMMUNITY_Project Endpoint Definitions|Project Endpoint Definitions]]
- [[_COMMUNITY_Plan & Checkout|Plan & Checkout]]
- [[_COMMUNITY_CORS Config|CORS Config]]
- [[_COMMUNITY_Member Controller Endpoints|Member Controller Endpoints]]
- [[_COMMUNITY_Application Entry Point|Application Entry Point]]
- [[_COMMUNITY_Chat Session DTOs|Chat Session DTOs]]
- [[_COMMUNITY_App Context Test|App Context Test]]
- [[_COMMUNITY_Payment Config Init|Payment Config Init]]
- [[_COMMUNITY_File DTOs|File DTOs]]
- [[_COMMUNITY_Subscription Billing|Subscription Billing]]
- [[_COMMUNITY_Usage API|Usage API]]
- [[_COMMUNITY_Plan Listing|Plan Listing]]
- [[_COMMUNITY_Billing Response DTOs|Billing Response DTOs]]
- [[_COMMUNITY_Project Request Node|Project Request Node]]
- [[_COMMUNITY_Preview Status Node|Preview Status Node]]

## God Nodes (most connected - your core abstractions)
1. `SubscriptionService` - 18 edges
2. `StripePaymentProcessor` - 18 edges
3. `Builder` - 17 edges
4. `SubscriptionServiceImpl` - 16 edges
5. `ProjectFileService` - 12 edges
6. `GlobalExceptionHandler` - 11 edges
7. `ProjectServiceImpl` - 11 edges
8. `AuthUtil` - 10 edges
9. `ProjectService` - 10 edges
10. `FileTreeContextAdvisor` - 9 edges

## Surprising Connections (you probably didn't know these)
- `MorphCode API Configuration` --references--> `ChatService`  [INFERRED]
  Diagrams/api-config.md → src/main/java/com/morphcode/ai/service/ChatService.java
- `MorphCode API Configuration` --references--> `PlanService`  [INFERRED]
  Diagrams/api-config.md → src/main/java/com/morphcode/ai/service/PlanService.java
- `MorphCode API Configuration` --references--> `ProjectMemberService`  [INFERRED]
  Diagrams/api-config.md → src/main/java/com/morphcode/ai/service/ProjectMemberService.java
- `MorphCode API Configuration` --references--> `ProjectService`  [INFERRED]
  Diagrams/api-config.md → src/main/java/com/morphcode/ai/service/ProjectService.java
- `MorphCode API Configuration` --references--> `SubscriptionService`  [INFERRED]
  Diagrams/api-config.md → src/main/java/com/morphcode/ai/service/SubscriptionService.java

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Authentication Flow (signup/login -> AuthService -> AuthResponse/UserProfileResponse)** — controller_authcontroller_signup, controller_authcontroller_login, service_authservice_ref, dto_signuprequest_record, dto_loginrequest_record, dto_authresponse_record, dto_userprofileresponse_record [EXTRACTED 1.00]
- **Billing and Payment Flow (plans, subscriptions, checkout, portal, webhooks)** — controller_billingcontroller_getallplans, controller_billingcontroller_getmysubscription, controller_billingcontroller_createcheckout, controller_billingcontroller_opencustomerportal, controller_billingcontroller_handlepaymentwebhooks, service_planservice_ref, service_subscriptionservice_ref, service_paymentprocessor_ref, config_paymentconfig_stripeinit [EXTRACTED 1.00]
- **AI Chat Flow (stream chat SSE + history via AiGenerationService + ChatService)** — controller_chatcontroller_streamchat, controller_chatcontroller_getchathistory, service_aigenerationservice_ref, service_chatservice_ref, config_aiconfig_chatclient, dto_chatrequest_record, dto_streamresponse_record, dto_chatresponse_record, dto_chateventresponse_record [INFERRED 0.95]
- **Project CRUD Operations** — controller_projectcontroller_getmyprojects, controller_projectcontroller_getprojectbyid, controller_projectcontroller_createproject, controller_projectcontroller_updateproject, controller_projectcontroller_deleteproject, service_projectservice_ref [EXTRACTED 1.00]
- **Project Member Management (invite, update role, remove)** — controller_projectmembercontroller_getmembers, controller_projectmembercontroller_invitemember, controller_projectmembercontroller_updatememberrole, controller_projectmembercontroller_removemember, service_projectmemberservice_ref [EXTRACTED 1.00]
- **Spring Configuration Beans (AI, CORS, Payment, Storage)** — config_aiconfig_chatclient, config_corsconfig_webmvcconfigurer, config_paymentconfig_stripeinit, config_storageconfig_minioclient, ai_morphcodeapplication_main [INFERRED 0.95]
- **Member Management DTOs** — member_invitememberrequest_invitememberrequest, member_memberresponse_memberresponse, member_updatememberrolerequest_updatememberrolerequest, concept_projectrole_projectrole [INFERRED 0.95]
- **Project File DTOs** — project_filecontentresponse_filecontentresponse, project_filenode_filenode, project_filetreeresponse_filetreeresponse [INFERRED 0.95]
- **Project CRUD DTOs** — project_projectrequest_projectrequest, project_projectresponse_projectresponse, project_projectsummaryresponse_projectsummaryresponse [INFERRED 0.95]
- **Subscription and Plan DTOs** — subscription_checkoutrequest_checkoutrequest, subscription_checkoutresponse_checkoutresponse, subscription_planlimitsresponse_planlimitsresponse, subscription_planresponse_planresponse, subscription_portalresponse_portalresponse, subscription_subscriptionresponse_subscriptionresponse, subscription_usagetodayresponse_usagetodayresponse [INFERRED 0.95]
- **Chat Domain Entities** — entity_chatevent_chatevent, entity_chatmessage_chatmessage, entity_chatsession_chatsession, entity_chatsessionid_chatsessionid [EXTRACTED 1.00]
- **Plan Entity and DTOs** — entity_plan_plan, subscription_planresponse_planresponse, subscription_planlimitsresponse_planlimitsresponse, subscription_checkoutrequest_checkoutrequest [INFERRED 0.85]
- **Core Project Domain Entities** — entity_project_project, entity_projectfile_projectfile, entity_projectmember_projectmember, entity_projectmemberid_projectmemberid [INFERRED 0.95]
- **User and Subscription Domain** — entity_user_user, entity_subscription_subscription, entity_usagelog_usagelog, enums_subscriptionstatus_subscriptionstatus [INFERRED 0.90]
- **Project Role-Based Access Control** — enums_projectrole_projectrole, enums_projectpermission_projectpermission, entity_projectmember_projectmember [EXTRACTED 1.00]
- **Centralized Error Handling Subsystem** — error_globalexceptionhandler_globalexceptionhandler, error_apierror_apierror, error_badrequestexception_badrequestexception, error_resourcenotfoundexception_resourcenotfoundexception [EXTRACTED 1.00]
- **LLM Pipeline Components** — llm_promptutils_promptutils, llm_llmresponseparser_llmresponseparser, advisors_filetreecontextadvisor_filetreecontextadvisor, tools_codegenerationtools_codegenerationtools [INFERRED 0.90]
- **Chat Event Type and Message Role System** — enums_chateventtype_chateventtype, enums_messagerole_messagerole, llm_llmresponseparser_llmresponseparser, mapper_chatmapper_chatmapper [INFERRED 0.75]
- **JWT Authentication Pipeline** — security_jwtauthfilter_jwtauthfilter, security_authutil_authutil, security_jwtuserprincipal_jwtuserprincipal, security_websecurityconfig_websecurityconfig [EXTRACTED 1.00]
- **Project Access Control** — security_securityexpressions_securityexpressions, security_authutil_authutil, repository_projectmemberrepository_projectmemberrepository [EXTRACTED 1.00]
- **MapStruct DTO Mapper Layer** — mapper_projectfilemapper_projectfilemapper, mapper_projectmapper_projectmapper, mapper_projectmembermapper_projectmembermapper, mapper_subscriptionmapper_subscriptionmapper, mapper_usermapper_usermapper [EXTRACTED 1.00]
- **JPA Repository Data Access Layer** — repository_chateventrepository_chateventrepository, repository_chatmessagerepository_chatmessagerepository, repository_chatsessionrepository_chatsessionrepository, repository_planrepository_planrepository, repository_projectfilerepository_projectfilerepository, repository_projectmemberrepository_projectmemberrepository, repository_projectrepository_projectrepository, repository_subscriptionrepository_subscriptionrepository, repository_usagelogrepository_usagelogrepository, repository_userrepository_userrepository [EXTRACTED 1.00]
- **Chat Domain Repositories** — repository_chateventrepository_chateventrepository, repository_chatmessagerepository_chatmessagerepository, repository_chatsessionrepository_chatsessionrepository [INFERRED 0.95]
- **Project Domain Repositories** — repository_projectrepository_projectrepository, repository_projectmemberrepository_projectmemberrepository, repository_projectfilerepository_projectfilerepository [INFERRED 0.95]
- **Subscription Domain Repositories** — repository_subscriptionrepository_subscriptionrepository, repository_planrepository_planrepository, repository_usagelogrepository_usagelogrepository [INFERRED 0.85]
- **Service Interface Layer** — service_chatservice_chatservice, service_paymentprocessor_paymentprocessor, service_planservice_planservice, service_projectfileservice_projectfileservice, service_projectmemberservice_projectmemberservice, service_projectservice_projectservice, service_projecttemplateservice_projecttemplateservice, service_subscriptionservice_subscriptionservice, service_usageservice_usageservice, service_userservice_userservice [INFERRED 0.95]
- **Service Implementation Layer** — impl_aigenerationserviceimpl_aigenerationserviceimpl, impl_authserviceimpl_authserviceimpl, impl_chatserviceimpl_chatserviceimpl, impl_planserviceimpl_planserviceimpl, impl_projectfileserviceimpl_projectfileserviceimpl, impl_projectmemberserviceimpl_projectmemberserviceimpl, impl_projectserviceimpl_projectserviceimpl, impl_projecttemplateserviceimpl_projecttemplateserviceimpl, impl_stripepaymentprocessor_stripepaymentprocessor, impl_subscriptionserviceimpl_subscriptionserviceimpl, impl_usageserviceimpl_usageserviceimpl, impl_userserviceimpl_userserviceimpl [INFERRED 0.95]
- **Subscription & Billing Subsystem** — service_subscriptionservice_subscriptionservice, service_planservice_planservice, service_paymentprocessor_paymentprocessor, impl_subscriptionserviceimpl_subscriptionserviceimpl, impl_planserviceimpl_planserviceimpl, impl_stripepaymentprocessor_stripepaymentprocessor [INFERRED 0.95]
- **Project Management Subsystem** — service_projectservice_projectservice, service_projectfileservice_projectfileservice, service_projectmemberservice_projectmemberservice, service_projecttemplateservice_projecttemplateservice, impl_projectserviceimpl_projectserviceimpl, impl_projectfileserviceimpl_projectfileserviceimpl, impl_projectmemberserviceimpl_projectmemberserviceimpl, impl_projecttemplateserviceimpl_projecttemplateserviceimpl [INFERRED 0.95]
- **AI Code Generation Subsystem** — impl_aigenerationserviceimpl_aigenerationserviceimpl, service_projectfileservice_projectfileservice, service_usageservice_usageservice, diagrams_project_architecture_intelligenceservice, diagrams_project_architecture_llmapi [INFERRED 0.85]
- **Infrastructure Services (MinIO + PostgreSQL)** — root_services_docker_compose_dockercompose, diagrams_project_architecture_miniostorage, impl_projectfileserviceimpl_projectfileserviceimpl, impl_projecttemplateserviceimpl_projecttemplateserviceimpl [INFERRED 0.85]
- **AI Code Generation Pipeline** — diagrams_ai_architecture_react_frontend, diagrams_ai_architecture_spring_boot_server, diagrams_ai_architecture_system_prompt, diagrams_ai_architecture_get_file_tree, diagrams_ai_architecture_llm, diagrams_ai_architecture_get_file_content_tool, diagrams_ai_architecture_minio [EXTRACTED 1.00]
- **Kubernetes Pod Execution Environment** — diagrams_code_execution_system_architecture_95ee32b790_pod, diagrams_code_execution_system_architecture_95ee32b790_runner_container, diagrams_code_execution_system_architecture_95ee32b790_syncer_container, diagrams_code_execution_system_architecture_95ee32b790_vite_dev_server, diagrams_code_execution_system_architecture_95ee32b790_network_policy [EXTRACTED 1.00]
- **Backend Orchestration Layer** — diagrams_code_execution_system_architecture_95ee32b790_spring_backend, diagrams_code_execution_system_architecture_95ee32b790_fabric8_k8s_client, diagrams_code_execution_system_architecture_95ee32b790_kubernetes_cluster, diagrams_code_execution_system_architecture_95ee32b790_minio, diagrams_code_execution_system_architecture_95ee32b790_redis [EXTRACTED 1.00]

## Communities (68 total, 9 thin omitted)

### Community 0 - "Spring Security Config"
Cohesion: 0.06
Nodes (36): AuthenticationConfiguration, AuthenticationManager, FilterChain, HttpSecurity, HttpServletRequest, HttpServletResponse, AuthServiceImpl, JwtUserPrincipal (+28 more)

### Community 1 - "AI Chat & Config"
Cohesion: 0.06
Nodes (36): ChatClient, AiConfig, UsageController, MorphCode API Configuration, Spring Cloud API Gateway, Execution Service (K8s Controller), Intelligence Service (AI Pipeline), LLM API (Claude/OpenAI) (+28 more)

### Community 2 - "Subscription & Roles"
Cohesion: 0.08
Nodes (28): SubscriptionServiceImpl, SubscriptionMapper, PlanRepository, SubscriptionRepository, Set, Mapping, Plan, PlanResponse (+20 more)

### Community 3 - "Storage & MinIO"
Cohesion: 0.08
Nodes (28): Builder, StorageConfig, MinIO Object Storage, FileNode, ProjectFileServiceImpl, ProjectTemplateServiceImpl, ProjectFileMapper, MinioClient (+20 more)

### Community 4 - "Project Service"
Cohesion: 0.11
Nodes (25): ProjectServiceImpl, ProjectMapper, ProjectWithRole, ProjectRepository, ProjectWithRole, List, Project, ProjectResponse (+17 more)

### Community 5 - "Stripe Payment"
Cohesion: 0.09
Nodes (23): BadRequestException, StripePaymentProcessor, Invoice, Price, PaymentProcessor, CheckoutRequest, CheckoutResponse, Instant (+15 more)

### Community 6 - "Auth API"
Cohesion: 0.08
Nodes (25): Collection, AuthController, Subscription, UsageLog, User, SubscriptionStatus Enum, GrantedAuthority, UserServiceImpl (+17 more)

### Community 7 - "Project Members"
Cohesion: 0.12
Nodes (21): ProjectMemberServiceImpl, ProjectMemberMapper, ProjectMemberService, Mapping, MemberResponse, ProjectMember, User, InviteMemberRequest (+13 more)

### Community 8 - "Chat API"
Cohesion: 0.09
Nodes (25): ChatRequest, ChatController, MessageRole Enum, ChatServiceImpl, ChatMapper, ServerSentEvent, ChatService, ChatResponse (+17 more)

### Community 9 - "LLM File Advisor"
Cohesion: 0.09
Nodes (20): FileTreeContextAdvisor, ChatClientRequest, ChatClientResponse, Project, ProjectFile, ProjectMember, ProjectMemberId, ProjectPermission Enum (+12 more)

### Community 10 - "Usage Tracking"
Cohesion: 0.11
Nodes (17): UsageServiceImpl, UsageLogRepository, SubscriptionService, LocalDate, Long, Optional, UsageLog, LocalDate (+9 more)

### Community 11 - "Error Handling"
Cohesion: 0.14
Nodes (18): AccessDeniedException, ApiError, ApiFieldError, AuthenticationException, ApiError(), BadRequestException, GlobalExceptionHandler, ResourceNotFoundException (+10 more)

### Community 12 - "Billing API"
Cohesion: 0.10
Nodes (19): BillingController, PlanServiceImpl, PlanService, Session, CheckoutRequest, CheckoutResponse, GetMapping, List (+11 more)

### Community 13 - "AI Architecture"
Cohesion: 0.11
Nodes (24): get_file_content(paths[]) Tool, Get the File Tree, LLM, MinIO Object Storage, PostgreSQL Database, React Frontend, Spring Boot Server, StringBuilder Buffer (Chunk Buffering) (+16 more)

### Community 14 - "Chat Event Parsing"
Cohesion: 0.14
Nodes (14): ChatEvent, ChatEventType Enum, LlmResponseParser, ChatEventRepository, ChatMessageRepository, ChatSessionRepository, ChatMessage, List (+6 more)

### Community 15 - "Project Permissions"
Cohesion: 0.19
Nodes (10): ProjectPermission, ProjectMemberRepository, SecurityExpressions, List, Long, Optional, ProjectMember, ProjectRole (+2 more)

### Community 16 - "Project REST Endpoints"
Cohesion: 0.21
Nodes (12): ProjectController, DeleteMapping, GetMapping, List, Long, PatchMapping, PostMapping, ProjectRequest (+4 more)

### Community 17 - "Domain Enums & Entities"
Cohesion: 0.13
Nodes (12): ChatEventType (enum), MessageRole (enum), PreviewStatus (enum), Project (entity), User (entity), ChatEvent, ChatMessage, ChatSession (+4 more)

### Community 18 - "App Startup & Beans"
Cohesion: 0.12
Nodes (17): MorphcodeApplication (Spring Boot Entry Point), AiConfig - ChatClient Bean, AiConfig - SimpleLoggerAdvisor, CorsConfig - WebMvcConfigurer Bean, PaymentConfig - Stripe API Key Init, StorageConfig - MinioClient Bean, BillingController - createCheckoutResponse endpoint, BillingController - handlePaymentWebhooks endpoint (+9 more)

### Community 19 - "Member REST Endpoints"
Cohesion: 0.21
Nodes (12): ProjectMemberController, DeleteMapping, GetMapping, InviteMemberRequest, List, Long, MemberResponse, PatchMapping (+4 more)

### Community 20 - "Project Service Interface"
Cohesion: 0.30
Nodes (6): ProjectService, List, Long, ProjectRequest, ProjectResponse, ProjectSummaryResponse

### Community 21 - "File Management API"
Cohesion: 0.33
Nodes (7): FileController, FileContentResponse, FileTreeResponse, GetMapping, Long, ResponseEntity, String

### Community 22 - "Auth Endpoint Definitions"
Cohesion: 0.25
Nodes (9): AuthController - getProfile endpoint, AuthController - login endpoint, AuthController - signup endpoint, AuthResponse DTO (token + UserProfileResponse), LoginRequest DTO (username email + password), SignupRequest DTO (username + name + password), UserProfileResponse DTO (id + email + name + avatarUrl), AuthService (referenced by AuthController) (+1 more)

### Community 23 - "Member DTOs"
Cohesion: 0.38
Nodes (7): ProjectRole (enum), UserProfileResponse, InviteMemberRequest, MemberResponse, UpdateMemberRoleRequest, ProjectResponse, ProjectSummaryResponse

### Community 24 - "Project Endpoint Definitions"
Cohesion: 0.33
Nodes (6): ProjectController - createProject endpoint, ProjectController - deleteProject (soft) endpoint, ProjectController - getMyProjects endpoint, ProjectController - getProjectById endpoint, ProjectController - updateProject endpoint, ProjectService (referenced by ProjectController)

### Community 25 - "Plan & Checkout"
Cohesion: 0.33
Nodes (5): Plan, CheckoutRequest, PlanLimitsResponse, PlanResponse, SubscriptionResponse

### Community 26 - "CORS Config"
Cohesion: 0.60
Nodes (3): CorsConfig, Bean, WebMvcConfigurer

### Community 27 - "Member Controller Endpoints"
Cohesion: 0.40
Nodes (5): ProjectMemberController - getProjectMembers endpoint, ProjectMemberController - inviteMember endpoint, ProjectMemberController - removeMember endpoint, ProjectMemberController - updateMemberRole endpoint, ProjectMemberService (referenced by ProjectMemberController)

### Community 29 - "Chat Session DTOs"
Cohesion: 0.50
Nodes (4): ChatController - getChatHistory endpoint, ChatEventResponse DTO (id + type + sequenceOrder + content + filePath + metadata), ChatResponse DTO (id + role + events + content + tokensUsed + createdAt), ChatService (referenced by ChatController)

### Community 32 - "File DTOs"
Cohesion: 0.67
Nodes (3): FileContentResponse, FileNode, FileTreeResponse

## Knowledge Gaps
- **107 isolated node(s):** `String`, `PlanResponse`, `SubscriptionResponse`, `CheckoutRequest`, `CheckoutResponse` (+102 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `Builder` connect `Storage & MinIO` to `Spring Security Config`, `AI Chat & Config`, `Subscription & Roles`, `Project Service`, `Stripe Payment`, `Project Members`, `Chat API`, `Usage Tracking`, `Chat Event Parsing`, `Domain Enums & Entities`?**
  _High betweenness centrality (0.125) - this node is a cross-community bridge._
- **Why does `MorphCode API Configuration` connect `AI Chat & Config` to `Project Members`, `Chat API`, `Usage Tracking`, `Billing API`, `Project Service Interface`?**
  _High betweenness centrality (0.089) - this node is a cross-community bridge._
- **Why does `SubscriptionService` connect `Usage Tracking` to `AI Chat & Config`, `Subscription & Roles`, `Project Service`, `Stripe Payment`, `Billing API`?**
  _High betweenness centrality (0.085) - this node is a cross-community bridge._
- **Are the 15 inferred relationships involving `Builder` (e.g. with `.minioClient()` and `.streamChat()`) actually correct?**
  _`Builder` has 15 INFERRED edges - model-reasoned connections that need verification._
- **Are the 3 inferred relationships involving `SubscriptionServiceImpl` (e.g. with `ProjectServiceImpl` and `PlanService`) actually correct?**
  _`SubscriptionServiceImpl` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `String`, `PlanResponse`, `SubscriptionResponse` to the rest of the system?**
  _108 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Spring Security Config` be split into smaller, more focused modules?**
  _Cohesion score 0.06328320802005012 - nodes in this community are weakly interconnected._