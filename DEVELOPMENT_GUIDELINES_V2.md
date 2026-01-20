# Development Guidelines v2
## Authoritative Guide for Development

**Last Updated**: 2026-01-20  
**Status**: Active  
**Inspired by**: Best practices from industry standards

---

## Sources

- `PROJECT_RULES.md` (primary rules for architecture, stack, structure)
- `DEVELOPMENT_GUIDELINES.md` (senior developer approach, code review)
- Project structure: `backend/`, `frontend/` (feature-based organization)
- Code quality: ESLint + TypeScript govern formatting/linting

---

## Scope

- Applies to entire project (backend + frontend)
- All code (Java/TypeScript/TSX/SQL) and dependencies
- All documentation and configuration files

---

## Non-Negotiable Guardrails

### Backend (Java/Spring Boot)
- ✅ **Functional components only** - No class components (use records, functional services)
- ✅ **Clean Architecture** - Domain logic never depends on infrastructure
- ✅ **Flyway migrations only** - All database changes via migrations, never manual
- ✅ **No hardcoded values** - Use constants, enums, configuration
- ✅ **Type safety** - Avoid `Object`, prefer specific types
- ✅ **Error handling** - Always handle exceptions, provide user-friendly messages
- ✅ **Cache strategy** - Use Spring Cache for frequently accessed data
- ✅ **Transaction management** - Use `@Transactional` appropriately

### Frontend (React/TypeScript)
- ✅ **Functional components only** - No class components
- ✅ **Hooks only** - Use hooks for state/effects, extract reusable logic to custom hooks
- ✅ **No hardcoded strings** - User-facing strings should be in constants (future: i18n)
- ✅ **Avoid `any`** - Require explicit types, use TypeScript strictly
- ✅ **Side effects** - Prefer derived data; if using `useEffect`, include cleanup and full deps
- ✅ **Component size** - Target < 500 lines per component
- ✅ **Feature-based structure** - Organize by features, not technical layers
- ✅ **Mobile-first** - Design for mobile screens first (320-430px width)

### General
- ✅ **No shortcuts** - No "quick fixes" that create technical debt
- ✅ **Code review required** - All code must be reviewed before push
- ✅ **Fix critical issues** - Never push code with critical issues
- ✅ **Documentation** - JSDoc/TSDoc for all public methods
- ✅ **Testing** - Write tests for critical paths (when testing infrastructure is added)

---

## Pre-Change Checklist

Before making any change:

- [ ] **Read relevant guidelines** - `PROJECT_RULES.md`, `DEVELOPMENT_GUIDELINES.md`
- [ ] **Identify target area** - Backend (`domain`, `application`, `infrastructure`, `api`) or Frontend (`features/`, `shared/`)
- [ ] **Plan i18n** - Consider user-facing strings (future: extract to i18n)
- [ ] **Plan error handling** - How will errors be handled and displayed?
- [ ] **Plan testing** - What needs to be tested? (when testing is added)
- [ ] **Review dependencies** - Will this require new packages? Evaluate impact
- [ ] **Consider scalability** - Will this work with 100+ users?
- [ ] **Check for duplication** - Is similar code already written?

---

## Development Commands

### Backend
- **Build**: `cd backend && mvn compile`
- **Run**: `cd backend && mvn spring-boot:run`
- **Test**: `cd backend && mvn test`
- **Database migration**: Automatically runs via Flyway on startup

### Frontend
- **Dev server**: `cd frontend && npm run dev` (Vite, typically `http://localhost:5173`)
- **Build**: `cd frontend && npm run build` (outputs to `dist/`)
- **Preview**: `cd frontend && npm run preview` (preview production build)
- **Lint**: `cd frontend && npm run lint` (auto-fix: add `--fix` flag)
- **Type check**: `cd frontend && npm run typecheck`

### Docker
- **Start all services**: `docker-compose up`
- **Rebuild**: `docker-compose up --build`
- **Stop**: `docker-compose down`

### Environment Variables
- Backend: Configure in `application.yml` or environment variables
- Frontend: Configure in `config.js` or via `window.APP_CONFIG`
- **Never commit secrets** - Use environment variables for production

---

## Implementation Rules

### Code Organization

**Backend Structure:**
```
backend/src/main/java/com/familyapp/
├── domain/           # Domain models (records, value objects)
├── application/      # Use cases, services (business logic)
├── infrastructure/   # Repositories, external integrations
└── api/              # Controllers, DTOs, request/response models
```

**Frontend Structure:**
```
frontend/src/
├── features/         # Feature-based organization
│   ├── calendar/
│   ├── todos/
│   └── ...
├── shared/           # Shared utilities, hooks, API clients
└── styles.css        # Global styles
```

### Component Design

**Backend Services:**
- Keep services focused (single responsibility)
- Target: < 500 lines per service
- Use dependency injection
- Document with JavaDoc

**Frontend Components:**
- Keep components small and focused
- Target: < 500 lines per component
- Extract reusable logic to hooks
- Use TypeScript strictly (no `any`)

### State Management

**Backend:**
- Use Spring's dependency injection
- Cache frequently accessed data
- Use transactions for data consistency

**Frontend:**
- Use React hooks (`useState`, `useEffect`, `useCallback`, `useMemo`)
- Extract complex state logic to custom hooks
- Avoid prop drilling (consider Context API for shared state)
- Prefer derived state over stored state when possible

### Error Handling

**Backend:**
- Always catch exceptions
- Return user-friendly error messages
- Log errors with context
- Use appropriate HTTP status codes

**Frontend:**
- Catch errors in async operations
- Display user-friendly error messages
- Log errors to console (in development)
- Consider error boundaries for component-level errors

### Performance

**Backend:**
- Use caching for frequently accessed data
- Optimize database queries (avoid N+1 queries)
- Use pagination for large datasets
- Monitor query performance

**Frontend:**
- Use `useMemo` for expensive calculations
- Use `useCallback` for stable function references
- Avoid unnecessary re-renders
- Optimize bundle size (code splitting when needed)

---

## Testing Rules (Future)

**When testing infrastructure is added:**

### Backend Testing
- **Unit tests** - Test domain logic and services in isolation
- **Integration tests** - Test API endpoints and database interactions
- **Use test containers** - For database integration tests
- **Target coverage** - 80%+ for critical paths

### Frontend Testing
- **Component tests** - Test components in isolation
- **Hook tests** - Test custom hooks
- **Integration tests** - Test user flows
- **Prefer semantic queries** - Use `getByRole`, `getByLabelText` over `getByTestId`
- **Target coverage** - 80%+ for critical paths

### Test Data
- Use factories/builders for test data
- Clean up test data after tests
- Use realistic test scenarios

---

## Code Review Checklist

### Critical Issues (Must Fix Before Push)
- [ ] Security vulnerabilities
- [ ] Data consistency issues
- [ ] Memory leaks
- [ ] Performance bottlenecks (N+1 queries, missing indexes)
- [ ] Missing error handling
- [ ] Code that will break at scale
- [ ] Missing validation
- [ ] Hardcoded secrets or credentials

### High Priority Issues (Should Fix)
- [ ] Code duplication
- [ ] Inconsistent patterns
- [ ] Poor error messages
- [ ] Missing documentation (JSDoc/TSDoc)
- [ ] Components/services > 500 lines
- [ ] Missing type safety (`any` types)
- [ ] Hardcoded strings (user-facing)

### Medium Priority Issues (Nice to Have)
- [ ] Code style improvements
- [ ] Minor optimizations
- [ ] Additional test coverage (when tests are added)
- [ ] Documentation enhancements
- [ ] Refactoring opportunities

---

## Safety Boundaries

### Never Do
- ❌ Never reset or revert user changes without confirmation
- ❌ Never skip error handling
- ❌ Never skip validation
- ❌ Never create technical debt without documenting it
- ❌ Never push code with critical issues
- ❌ Never commit secrets or credentials
- ❌ Never bypass lint/format rules without justification

### Always Do
- ✅ Always handle errors gracefully
- ✅ Always validate input
- ✅ Always document complex logic
- ✅ Always review code before push
- ✅ Always test manually before push
- ✅ Always use environment variables for secrets
- ✅ Always follow the project structure

---

## Dependency Rules

### Before Adding a New Dependency

Evaluate:
1. **Functionality fit** - Does it solve the problem well?
2. **Maintenance** - Is it actively maintained? Recent updates?
3. **Documentation** - Is it well documented?
4. **Bundle size** - What's the impact on bundle size? (frontend)
5. **Dependency chain** - What dependencies does it bring?
6. **TypeScript support** - Does it have TypeScript types?
7. **Security** - Any known vulnerabilities?
8. **License** - Is the license compatible?

### Version Management
- Avoid `latest` - Use specific versions or semver ranges
- Document version overrides if needed
- Run `npm audit` / `mvn dependency-check` regularly
- Remove unused dependencies

### Current Stack
- **Backend**: Java 21+, Spring Boot, MySQL, Flyway
- **Frontend**: React 18+, TypeScript, Vite, PWA
- **Avoid adding** - Heavy frameworks unless necessary (e.g., Redux, Zustand - only if needed)

---

## When In Doubt

### Escalation
- If a rule conflicts with a feature request → Ask for clarification
- If required guidance is missing → Stop and ask
- If unsure about architecture decision → Document and discuss
- If security concern → Always escalate

### Decision Making
- **Prefer simplicity** - Simple solutions over complex ones
- **Prefer maintainability** - Code that's easy to understand
- **Prefer stability** - Proven solutions over cutting-edge
- **Document decisions** - Write down why, not just what

---

## Workflow

### Phase 1: Planning
1. Understand requirements
2. Design solution
3. Create implementation plan
4. Review with guidelines

### Phase 2: Implementation
1. Write code following guidelines
2. Add error handling
3. Add validation
4. Add documentation

### Phase 3: Code Review
1. Self-review using checklist
2. Identify issues (critical/high/medium)
3. Fix critical issues
4. Document fixes

### Phase 4: Testing
1. Manual testing
2. Verify edge cases
3. Check error scenarios
4. Verify performance

### Phase 5: Push
1. All critical issues fixed
2. Code reviewed
3. Tested manually
4. Ready for production

---

## Success Criteria

Code is considered "done" when:
1. ✅ Implementation is complete
2. ✅ Code review has been performed
3. ✅ All critical issues have been fixed
4. ✅ Code is documented (JSDoc/TSDoc)
5. ✅ Code follows best practices
6. ✅ No shortcuts were taken
7. ✅ Solution is robust and scalable
8. ✅ Manually tested
9. ✅ No linter errors
10. ✅ Builds successfully

---

## Examples

### ❌ Bad: Taking Shortcuts
```typescript
// Quick fix - will cause problems later
function updateMember(id: string, name: string) {
  members.find(m => m.id === id)!.name = name;
  // No error handling, no validation, no state update
}
```

### ✅ Good: Robust Solution
```typescript
/**
 * Updates a member's name.
 * 
 * @param id - The member ID
 * @param name - The new name
 * @throws Error if member not found or name invalid
 */
function updateMember(id: string, name: string): void {
  // Validate input
  if (!name || name.trim().isEmpty()) {
    throw new Error("Name cannot be empty");
  }
  
  // Find member with proper error handling
  const member = members.find(m => m.id === id);
  if (!member) {
    throw new Error(`Member not found: ${id}`);
  }
  
  // Update with trimmed name
  member.name = name.trim();
  
  // Update state/cache
  setMembers([...members]);
}
```

---

## Remember

> "The best time to write good code is the first time. The second best time is now."

- Don't create technical debt
- Don't take shortcuts
- Always think long-term
- Always review your code
- Always fix critical issues
- Always test before push

---

## Questions to Ask Before Implementation

1. **Is this the right solution?**
   - Will it scale?
   - Is it maintainable?
   - Does it follow best practices?

2. **Am I taking any shortcuts?**
   - Am I skipping error handling?
   - Am I skipping validation?
   - Am I creating technical debt?

3. **What could go wrong?**
   - What are the edge cases?
   - What happens if it fails?
   - What happens at scale?

4. **Is this code review ready?**
   - Would I be proud to show this code?
   - Would a senior developer approve this?
   - Are there any obvious issues?

---

## Final Note

These guidelines ensure that:
- Code quality remains high
- Technical debt is minimized
- Solutions are robust and scalable
- Code is maintainable long-term
- Team can work efficiently

**Always follow these guidelines. No exceptions.**
