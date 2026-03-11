# Development Guidelines
## Senior Developer Approach

**Last Updated**: 2026-01-18  
**Status**: Active

---

## Core Principles

### 1. **Always Work as Senior Developer**
- Think long-term, not short-term
- Consider scalability, maintainability, and reliability
- Write code that other developers can understand and maintain
- Follow best practices and industry standards

### 2. **Never Take Shortcuts**
- No "quick fixes" that create technical debt
- No temporary solutions that will need to be redone
- No code that "works but isn't quite right"
- Always implement proper solutions from the start

### 3. **Robust and Well-Thought-Out Solutions**
- Design before implementation
- Consider edge cases and error scenarios
- Plan for future requirements
- Choose solutions that scale

### 4. **Always Review Code After Implementation**
- Perform code review on all implemented code
- Identify critical issues
- Fix critical issues before considering work "done"
- Document findings and improvements

---

## Workflow

### Phase 1: Planning
1. **Understand Requirements**
   - Ask clarifying questions if needed
   - Identify edge cases
   - Consider scalability implications

2. **Design Solution**
   - Choose appropriate patterns
   - Consider maintainability
   - Plan for error handling
   - Document design decisions

3. **Implementation Plan**
   - Break down into steps
   - Identify dependencies
   - Plan testing approach

### Phase 2: Implementation
1. **Write Code**
   - Follow best practices
   - Write clean, readable code
   - Add proper error handling
   - Include documentation (Javadoc/TSDoc)

2. **No Shortcuts**
   - Don't skip error handling
   - Don't skip validation
   - Don't skip documentation
   - Don't create technical debt

### Phase 3: Code Review
1. **Self-Review**
   - Review all implemented code
   - Check for:
     - Code duplication
     - Error handling
     - Edge cases
     - Performance issues
     - Security concerns
     - Maintainability

2. **Identify Issues**
   - Critical issues (must fix)
   - High priority issues (should fix)
   - Medium priority issues (nice to have)

3. **Fix Critical Issues**
   - Address all critical issues
   - Document fixes
   - Verify fixes work correctly

### Phase 4: Documentation
1. **Code Documentation**
   - Javadoc/TSDoc for all public methods
   - Inline comments for complex logic
   - README updates if needed

2. **Change Documentation**
   - Document what was changed
   - Document why it was changed
   - Document any breaking changes

---

## Code Review Checklist

### Critical Issues (Must Fix)
- [ ] Security vulnerabilities
- [ ] Data consistency issues
- [ ] Memory leaks
- [ ] Performance bottlenecks
- [ ] Missing error handling
- [ ] Code that will break at scale

### High Priority Issues (Should Fix)
- [ ] Code duplication
- [ ] Inconsistent patterns
- [ ] Missing validation
- [ ] Poor error messages
- [ ] Missing documentation

### Medium Priority Issues (Nice to Have)
- [ ] Code style improvements
- [ ] Minor optimizations
- [ ] Additional test coverage
- [ ] Documentation enhancements

---

## Examples

### ❌ Bad: Taking Shortcuts
```java
// Quick fix - will cause problems later
public void updateMember(UUID id, String name) {
    repository.findById(id).get().setName(name);
    // No error handling, no validation, no cache update
}
```

### ✅ Good: Robust Solution
```java
/**
 * Updates a member's name.
 * 
 * @param id The member ID
 * @param name The new name
 * @throws IllegalArgumentException if member not found or name invalid
 */
public Member updateMember(UUID id, String name) {
    // Validate input
    if (name == null || name.trim().isEmpty()) {
        throw new IllegalArgumentException("Name cannot be empty");
    }
    
    // Find member with proper error handling
    var member = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Member not found: " + id));
    
    // Update
    member.setName(name.trim());
    var saved = repository.save(member);
    
    // Update cache
    cacheService.evictMember(id);
    
    return toDomain(saved);
}
```

---

## When to Apply These Guidelines

**Always apply these guidelines when:**
- Implementing new features
- Fixing bugs
- Refactoring code
- Adding new dependencies
- Modifying existing functionality

**Exception:**
- Only in emergency hotfixes (and even then, document technical debt created)

---

## Success Criteria

Code is considered "done" when:
1. ✅ Implementation is complete
2. ✅ Code review has been performed
3. ✅ All critical issues have been fixed
4. ✅ Code is documented
5. ✅ Code follows best practices
6. ✅ No shortcuts were taken
7. ✅ Solution is robust and scalable

---

## Remember

> "The best time to write good code is the first time. The second best time is now."

- Don't create technical debt
- Don't take shortcuts
- Always think long-term
- Always review your code
- Always fix critical issues

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
