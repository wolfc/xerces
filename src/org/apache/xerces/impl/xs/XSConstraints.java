/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 1999,2000 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.xerces.impl.xs;

import org.apache.xerces.impl.dv.XSSimpleType;
import org.apache.xerces.impl.dv.XSUnionSimpleType;
import org.apache.xerces.impl.dv.InvalidDatatypeValueException;
import org.apache.xerces.impl.dv.ValidatedInfo;
import org.apache.xerces.impl.XMLErrorReporter;
import org.apache.xerces.impl.xs.models.CMBuilder;
import org.apache.xerces.impl.xs.models.XSCMValidator;
import org.apache.xerces.impl.validation.ValidationContext;
import org.apache.xerces.util.SymbolHash;
import java.util.Vector;

/**
 * Constaints shared by traversers and validator
 *
 * @author Sandy Gao, IBM
 *
 * @version $Id$
 */
public class XSConstraints {

    static final int OCCURRENCE_UNKNOWN = SchemaSymbols.OCCURRENCE_UNBOUNDED-1;
    /**
     * check whether derived is valid derived from base, given a subset
     * of {restriction, extension}.
     */
    public static boolean checkTypeDerivationOk(XSTypeDecl derived, XSTypeDecl base, short block) {
        // if derived is anyType, then it's valid only if base is anyType too
        if (derived == SchemaGrammar.fAnyType)
            return derived == base;
        // if derived is anySimpleType, then it's valid only if the base
        // is ur-type
        if (derived == SchemaGrammar.fAnySimpleType) {
            return (base == SchemaGrammar.fAnyType ||
                    base == SchemaGrammar.fAnySimpleType);
        }

        // if derived is simple type
        if (derived.getXSType() == XSTypeDecl.SIMPLE_TYPE) {
            // if base is complex type
            if (base.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
                // if base is anyType, change base to anySimpleType,
                // otherwise, not valid
                if (base == SchemaGrammar.fAnyType)
                    base = SchemaGrammar.fAnySimpleType;
                else
                    return false;
            }
            return checkSimpleDerivation((XSSimpleType)derived,
                                         (XSSimpleType)base, block);
        } else {
            return checkComplexDerivation((XSComplexTypeDecl)derived, base, block);
        }
    }

    /**
     * check whether simple type derived is valid derived from base,
     * given a subset of {restriction, extension}.
     */
    public static boolean checkSimpleDerivationOk(XSSimpleType derived, XSTypeDecl base, short block) {
        // if derived is anySimpleType, then it's valid only if the base
        // is ur-type
        if (derived == SchemaGrammar.fAnySimpleType) {
            return (base == SchemaGrammar.fAnyType ||
                    base == SchemaGrammar.fAnySimpleType);
        }

        // if base is complex type
        if (base.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
            // if base is anyType, change base to anySimpleType,
            // otherwise, not valid
            if (base == SchemaGrammar.fAnyType)
                base = SchemaGrammar.fAnySimpleType;
            else
                return false;
        }
        return checkSimpleDerivation((XSSimpleType)derived,
                                     (XSSimpleType)base, block);
    }

    /**
     * check whether complex type derived is valid derived from base,
     * given a subset of {restriction, extension}.
     */
    public static boolean checkComplexDerivationOk(XSComplexTypeDecl derived, XSTypeDecl base, short block) {
        // if derived is anyType, then it's valid only if base is anyType too
        if (derived == SchemaGrammar.fAnyType)
            return derived == base;
        return checkComplexDerivation((XSComplexTypeDecl)derived, base, block);
    }

    /**
     * Note: this will be a private method, and it assumes that derived is not
     *       anySimpleType, and base is not anyType. Another method will be
     *       introduced for public use, which will call this method.
     */
    private static boolean checkSimpleDerivation(XSSimpleType derived, XSSimpleType base, short block) {
        // 1 They are the same type definition.
        if (derived == base)
            return true;

        // 2 All of the following must be true:
        // 2.1 restriction is not in the subset, or in the {final} of its own {base type definition};
        if ((block & SchemaSymbols.RESTRICTION) != 0 ||
            (derived.getBaseType().getFinalSet() & SchemaSymbols.RESTRICTION) != 0) {
            return false;
        }

        // 2.2 One of the following must be true:
        // 2.2.1 D's base type definition is B.
        XSSimpleType directBase = (XSSimpleType)derived.getBaseType();
        if (directBase == base)
            return true;

        // 2.2.2 D's base type definition is not the simple ur-type definition and is validly derived from B given the subset, as defined by this constraint.
        if (directBase != SchemaGrammar.fAnySimpleType &&
            checkSimpleDerivation(directBase, base, block)) {
            return true;
        }

        // 2.2.3 D's {variety} is list or union and B is the simple ur-type definition.
        if ((derived.getVariety() == XSSimpleType.VARIETY_LIST ||
             derived.getVariety() == XSSimpleType.VARIETY_UNION) &&
            base == SchemaGrammar.fAnySimpleType) {
            return true;
        }

        // 2.2.4 B's {variety} is union and D is validly derived from a type definition in B's {member type definitions} given the subset, as defined by this constraint.
        if (base.getVariety() == XSSimpleType.VARIETY_UNION) {
            XSSimpleType[] subUnionMemberDV = ((XSUnionSimpleType)base).getMemberTypes();
            int subUnionSize = subUnionMemberDV.length ;
            for (int i=0; i<subUnionSize; i++) {
                base = subUnionMemberDV[i];
                if (checkSimpleDerivation(derived, base, block))
                    return true;
            }
        }

        return false;
    }

    /**
     * Note: this will be a private method, and it assumes that derived is not
     *       anyType. Another method will be introduced for public use,
     *       which will call this method.
     */
    private static boolean checkComplexDerivation(XSComplexTypeDecl derived, XSTypeDecl base, short block) {
        // 2.1 B and D must be the same type definition.
        if (derived == base)
            return true;

        // 1 If B and D are not the same type definition, then the {derivation method} of D must not be in the subset.
        if ((derived.fDerivedBy & block) != 0)
            return false;

        // 2 One of the following must be true:
        XSTypeDecl directBase = derived.fBaseType;
        // 2.2 B must be D's {base type definition}.
        if (directBase == base)
            return true;

        // 2.3 All of the following must be true:
        // 2.3.1 D's {base type definition} must not be the ur-type definition.
        if (directBase == SchemaGrammar.fAnyType ||
            directBase == SchemaGrammar.fAnySimpleType) {
            return false;
        }

        // 2.3.2 The appropriate case among the following must be true:
        // 2.3.2.1 If D's {base type definition} is complex, then it must be validly derived from B given the subset as defined by this constraint.
        if (directBase.getXSType() == XSTypeDecl.COMPLEX_TYPE)
            return checkComplexDerivation((XSComplexTypeDecl)directBase, base, block);

        // 2.3.2.2 If D's {base type definition} is simple, then it must be validly derived from B given the subset as defined in Type Derivation OK (Simple) (3.14.6).
        if (directBase.getXSType() == XSTypeDecl.SIMPLE_TYPE) {
            // if base is complex type
            if (base.getXSType() == XSTypeDecl.COMPLEX_TYPE) {
                // if base is anyType, change base to anySimpleType,
                // otherwise, not valid
                if (base == SchemaGrammar.fAnyType)
                    base = SchemaGrammar.fAnySimpleType;
                else
                    return false;
            }
            return checkSimpleDerivation((XSSimpleType)directBase,
                                         (XSSimpleType)base, block);
        }

        return false;
    }

    /**
     * check whether a value is a valid default for some type
     * returns the compiled form of the value
     * The parameter value could be either a String or a ValidatedInfo object
     */
    public static Object ElementDefaultValidImmediate(XSTypeDecl type, Object value, ValidationContext context, ValidatedInfo vinfo) {

        XSSimpleType dv = null;

        // e-props-correct
        // For a string to be a valid default with respect to a type definition the appropriate case among the following must be true:
        // 1 If the type definition is a simple type definition, then the string must be valid with respect to that definition as defined by String Valid (3.14.4).
        if (type.getXSType() == XSTypeDecl.SIMPLE_TYPE) {
            dv = (XSSimpleType)type;
        }

        // 2 If the type definition is a complex type definition, then all of the following must be true:
        else {
            // 2.1 its {content type} must be a simple type definition or mixed.
            XSComplexTypeDecl ctype = (XSComplexTypeDecl)type;
            // 2.2 The appropriate case among the following must be true:
            // 2.2.1 If the {content type} is a simple type definition, then the string must be valid with respect to that simple type definition as defined by String Valid (3.14.4).
            if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_SIMPLE) {
                dv = ctype.fXSSimpleType;
            }
            // 2.2.2 If the {content type} is mixed, then the {content type}'s particle must be emptiable as defined by Particle Emptiable (3.9.6).
            else if (ctype.fContentType == XSComplexTypeDecl.CONTENTTYPE_MIXED) {
                if (!ctype.fParticle.emptiable())
                    return null;
            }
            else {
                return null;
            }
        }

        // get the simple type declaration, and validate
        Object actualValue = null;
        if (dv != null) {
            try {
                if (value instanceof String) {
                    actualValue = dv.validate((String)value, context, vinfo);
                } else {
                    ValidatedInfo info = (ValidatedInfo)value;
                    dv.validate(context, info);
                    actualValue = info.actualValue;
                }
            } catch (InvalidDatatypeValueException ide) {
                return null;
            }
        }

        return actualValue;
    }

    /**
     * used to check the 3 constraints against each complex type
     * (should be each model group):
     * Unique Particle Attribution, Particle Derivation (Restriction),
     * Element Declrations Consistent.
     */
    public static void fullSchemaChecking(XSGrammarBucket grammarBucket,
                                          SubstitutionGroupHandler SGHandler,
                                          CMBuilder cmBuilder,
                                          XMLErrorReporter errorReporter) {
        // get all grammars, and put all substitution group information
        // in the substitution group handler
        SchemaGrammar[] grammars = grammarBucket.getGrammars();
        for (int i = grammars.length-1; i >= 0; i--) {
            SGHandler.addSubstitutionGroup(grammars[i].getSubstitutionGroups());
        }

        // before worrying about complexTypes, let's get
        // groups redefined by restriction out of the way.
        for (int g = grammars.length-1; g >= 0; g--) {
            XSGroupDecl [] redefinedGroups = grammars[g].getRedefinedGroupDecls();
            for(int i=0; i<redefinedGroups.length; ) {
                XSGroupDecl derivedGrp = redefinedGroups[i++];
                XSParticleDecl derivedParticle = derivedGrp.fParticle;
                XSGroupDecl baseGrp = redefinedGroups[i++];
                XSParticleDecl baseParticle = baseGrp.fParticle;
                if(baseParticle == null) {
                    if(derivedParticle != null) { // can't be a restriction!
                        errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                        "src-redefine.6.2.2",
                                        new Object[]{derivedGrp.fName, "rcase-Recurse.2"},
                                        XMLErrorReporter.SEVERITY_ERROR);
                    } 
                } else {
                    try {
                        particleValidRestriction(SGHandler, 
                            derivedParticle, baseParticle);
                    } catch (XMLSchemaException e) {
                        String key = e.getKey();
                        errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                        key,
                                        e.getArgs(),
                                        XMLErrorReporter.SEVERITY_ERROR);
                        errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                        "src-redefine.6.2.2",
                                        new Object[]{derivedGrp.fName, key},
                                        XMLErrorReporter.SEVERITY_ERROR);
                    }
                }
            }
        }

        // for each complex type, check the 3 constraints.
        // types need to be checked
        XSComplexTypeDecl[] types;
        // to hold the errors
        // REVISIT: do we want to report all errors? or just one?
        //XMLSchemaError1D errors = new XMLSchemaError1D();
        // whether need to check this type again;
        // whether only do UPA checking
        boolean further, fullChecked;
        // if do all checkings, how many need to be checked again.
        int keepType;
        // i: grammar; j: type; k: error
        // for all grammars
        SymbolHash elemTable = new SymbolHash();
        for (int i = grammars.length-1, j, k; i >= 0; i--) {
            // get whether only check UPA, and types need to be checked
            keepType = 0;
            fullChecked = grammars[i].fFullChecked;
            types = grammars[i].getUncheckedComplexTypeDecls();
            // for each type
            for (j = types.length-1; j >= 0; j--) {
                // if only do UPA checking, skip the other two constraints
                if (!fullChecked) {
                // 1. Element Decl Consistent
                  if (types[j].fParticle!=null) {
                    elemTable.clear();
                    try {
                      checkElementDeclsConsistent(types[j], types[j].fParticle,
                                                  elemTable, SGHandler);
                    }
                    catch (XMLSchemaException e) {
                      errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                                e.getKey(),
                                                e.getArgs(),
                                                XMLErrorReporter.SEVERITY_ERROR);
                    }
                  }
                }

                // 2. Particle Derivation
                if (types[j].fBaseType != null &&
                    types[j].fBaseType != SchemaGrammar.fAnyType &&
                    types[j].fDerivedBy == SchemaSymbols.RESTRICTION &&
                    types[j].fParticle !=null &&
                    (types[j].fBaseType instanceof XSComplexTypeDecl) &&
                    ((XSComplexTypeDecl)(types[j].fBaseType)).fParticle != null) {
                  try {
                      particleValidRestriction(SGHandler, types[j].fParticle,
                         ((XSComplexTypeDecl)(types[j].fBaseType)).fParticle);
                  } catch (XMLSchemaException e) {
                      errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                                e.getKey(),
                                                e.getArgs(),
                                                XMLErrorReporter.SEVERITY_ERROR);
                      errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                                "derivation-ok-restriction.5.3",
                                                new Object[]{types[j].fName},
                                                XMLErrorReporter.SEVERITY_ERROR);
                  }
                }

                // 3. UPA
                // get the content model and check UPA
                XSCMValidator cm = types[j].getContentModel(cmBuilder);
                further = false;
                if (cm != null) {
                    try {
                        further = cm.checkUniqueParticleAttribution(SGHandler);
                    } catch (XMLSchemaException e) {
                        errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                                  e.getKey(),
                                                  e.getArgs(),
                                                  XMLErrorReporter.SEVERITY_ERROR);
                    }
                }
                // now report all errors
                // REVISIT: do we want to report all errors? or just one?
                /*for (k = errors.getErrorCodeNum()-1; k >= 0; k--) {
                    errorReporter.reportError(XSMessageFormatter.SCHEMA_DOMAIN,
                                              errors.getErrorCode(k),
                                              errors.getArgs(k),
                                              XMLErrorReporter.SEVERITY_ERROR);
                }*/

                // if we are doing all checkings, and this one needs further
                // checking, store it in the type array.
                if (!fullChecked && further)
                    types[keepType++] = types[j];

                // clear errors for the next type.
                // REVISIT: do we want to report all errors? or just one?
                //errors.clear();
            }
            // we've done with the types in this grammar. if we are checking
            // all constraints, need to trim type array to a proper size:
            // only contain those need further checking.
            // and mark this grammar that it only needs UPA checking.
            if (!fullChecked) {
                grammars[i].setUncheckedTypeNum(keepType);
                grammars[i].fFullChecked = true;
            }
        }
    }

    /*
       Check that a given particle is a valid restriction of a base particle.
    */

    public static void checkElementDeclsConsistent(XSComplexTypeDecl type,
                                     XSParticleDecl particle,
                                     SymbolHash elemDeclHash,
                                     SubstitutionGroupHandler sgHandler) 
                                     throws XMLSchemaException {

       // check for elements in the tree with the same name and namespace           

       int pType = particle.fType;

       if (pType == XSParticleDecl.PARTICLE_EMPTY ||
           pType == XSParticleDecl.PARTICLE_WILDCARD)
          return;

       if (pType == XSParticleDecl.PARTICLE_ELEMENT) {
          XSElementDecl elem = (XSElementDecl)(particle.fValue);
          findElemInTable(type, elem, elemDeclHash);

          if (elem.isGlobal()) {
             // Check for subsitution groups.  
             XSElementDecl[] subGroup = sgHandler.getSubstitutionGroup(elem);
             for (int i = 0; i < subGroup.length; i++) {
               findElemInTable(type, subGroup[i], elemDeclHash); 
             }
          }
          return;
       }

       XSParticleDecl left = (XSParticleDecl)particle.fValue;
       checkElementDeclsConsistent(type, left, elemDeclHash, sgHandler);

       XSParticleDecl right = (XSParticleDecl)particle.fOtherValue;
       if (right != null) {
         checkElementDeclsConsistent(type, right, elemDeclHash, sgHandler);
       }
       
    }

    public static void findElemInTable(XSComplexTypeDecl type, XSElementDecl elem, 
                                       SymbolHash elemDeclHash) 
                                       throws XMLSchemaException {

        // How can we avoid this concat?  LM. 
        String name = elem.fName + "," + elem.fTargetNamespace;

        XSElementDecl existingElem = null;
        if ((existingElem = (XSElementDecl)(elemDeclHash.get(name))) == null) {
          // just add it in
          elemDeclHash.put(name, elem);
        }
        else {
          // If this is the same check element, we're O.K. 
          if (elem == existingElem) 
            return;

          if (elem.fType != existingElem.fType) {
            // Types are not the same 
            throw new XMLSchemaException("cos-element-consistent", 
                      new Object[] {type.fName, name});

          }
        }
    }

    // Invoke particleValidRestriction with a substitution group handler for each
    /*
       Check that a given particle is a valid restriction of a base particle.
    */

    public static void particleValidRestriction(SubstitutionGroupHandler sgHandler,
                                     XSParticleDecl dParticle,
                                     XSParticleDecl bParticle)
                                     throws XMLSchemaException {

    // Invoke particleValidRestriction with a substitution group handler for each
    // particle.

       particleValidRestriction(dParticle, sgHandler, bParticle, sgHandler);
    }

    private static void particleValidRestriction(XSParticleDecl dParticle,
                                     SubstitutionGroupHandler dSGHandler,
                                     XSParticleDecl bParticle,
                                     SubstitutionGroupHandler bSGHandler)
                                     throws XMLSchemaException {

       Vector dChildren = null;
       Vector bChildren = null;
       int dMinEffectiveTotalRange=OCCURRENCE_UNKNOWN;
       int dMaxEffectiveTotalRange=OCCURRENCE_UNKNOWN;


       // Check for empty particles.   If either base or derived particle is empty,
       // (and the other isn't) it's an error.
       if (! (dParticle.isEmpty() == bParticle.isEmpty())) {
         throw new XMLSchemaException("cos-particle-restrict", null);
       }

       //
       // Do setup prior to invoking the Particle (Restriction) cases.
       // This involves:
       //   - removing pointless occurrences for groups, and retrieving a vector of
       //     non-pointless children
       //   - turning top-level elements with substitution groups into CHOICE groups.
       //

       int dType = dParticle.fType;
       //
       // Handle pointless groups for the derived particle
       //
       if (dType == XSParticleDecl.PARTICLE_SEQUENCE ||
           dType == XSParticleDecl.PARTICLE_CHOICE ||
           dType == XSParticleDecl.PARTICLE_ALL) {

         // Find a group, starting with this particle, with more than 1 child.   There
         // may be none, and the particle of interest trivially becomes an element or
         // wildcard.
         XSParticleDecl dtmp = getNonUnaryGroup(dParticle);
         if (dtmp != dParticle) {
            // Particle has been replaced.   Retrieve new type info.
            dParticle = dtmp;
            dType = dParticle.fType;
         }

         // Fill in a vector with the children of the particle, removing any
         // pointless model groups in the process.
         dChildren = removePointlessChildren(dParticle);
       }

       int dMinOccurs = dParticle.fMinOccurs;
       int dMaxOccurs = dParticle.fMaxOccurs;

       //
       // For elements which are the heads of substitution groups, treat as CHOICE
       //
       if (dSGHandler != null && dType == XSParticleDecl.PARTICLE_ELEMENT) {
           XSElementDecl dElement = (XSElementDecl)dParticle.fValue;

           if (dElement.isGlobal()) {
             // Check for subsitution groups.   Treat any element that has a
             // subsitution group as a choice.   Fill in the children vector with the
             // members of the substitution group
             XSElementDecl[] subGroup = dSGHandler.getSubstitutionGroup(dElement);
             if (subGroup.length >0 ) {
                // Now, set the type to be CHOICE.  The "group" will have the same
                // occurrence information as the original particle.
                dType = XSParticleDecl.PARTICLE_CHOICE;
                dMinOccurs = dParticle.fMinOccurs;
                dMaxOccurs = dParticle.fMaxOccurs;
                dMinEffectiveTotalRange = dMinOccurs;
                dMaxEffectiveTotalRange = dMaxOccurs;

                // Fill in the vector of children
                dChildren = new Vector(subGroup.length+1);
                for (int i = 0; i < subGroup.length; i++) {
                  addElementToParticleVector(dChildren, subGroup[i]);
                }
                addElementToParticleVector(dChildren, dElement);

                // Set the handler to null, to indicate that we've finished handling
                // substitution groups for this particle.
                dSGHandler = null;
             }
           }
       }

       int bType = bParticle.fType;
       //
       // Handle pointless groups for the base particle
       //
       if (bType == XSParticleDecl.PARTICLE_SEQUENCE ||
           bType == XSParticleDecl.PARTICLE_CHOICE ||
           bType == XSParticleDecl.PARTICLE_ALL) {

         // Find a group, starting with this particle, with more than 1 child.   There
         // may be none, and the particle of interest trivially becomes an element or
         // wildcard.
         XSParticleDecl btmp = getNonUnaryGroup(bParticle);
         if (btmp != bParticle) {
            // Particle has been replaced.   Retrieve new type info.
            bParticle = btmp;
            bType = bParticle.fType;
         }

         // Fill in a vector with the children of the particle, removing any
         // pointless model groups in the process.
         bChildren = removePointlessChildren(bParticle);
       }

       int bMinOccurs = bParticle.fMinOccurs;
       int bMaxOccurs = bParticle.fMaxOccurs;

       if (bSGHandler != null && bType == XSParticleDecl.PARTICLE_ELEMENT) {
           XSElementDecl bElement = (XSElementDecl)bParticle.fValue;

           if (bElement.isGlobal()) {
             // Check for subsitution groups.   Treat any element that has a
             // subsitution group as a choice.   Fill in the children vector with the
             // members of the substitution group
             XSElementDecl[] bsubGroup = bSGHandler.getSubstitutionGroup(bElement);
             if (bsubGroup.length >0 ) {
                // Now, set the type to be CHOICE
                bType = XSParticleDecl.PARTICLE_CHOICE;
                bMinOccurs = bParticle.fMinOccurs;
                bMaxOccurs = bParticle.fMaxOccurs;

                bChildren = new Vector(bsubGroup.length+1);
                for (int i = 0; i < bsubGroup.length; i++) {
                  addElementToParticleVector(bChildren, bsubGroup[i]);
                }
                addElementToParticleVector(bChildren, bElement);
                // Set the handler to null, to indicate that we've finished handling
                // substitution groups for this particle.
                bSGHandler = null;
             }
           }
       }

       //
       // O.K. - Figure out which particle derivation rule applies and call it
       //
       switch (dType) {
         case XSParticleDecl.PARTICLE_ELEMENT:
         {
            switch (bType) {

              // Elt:Elt NameAndTypeOK
              case XSParticleDecl.PARTICLE_ELEMENT:
              {
                 checkNameAndTypeOK((XSElementDecl)dParticle.fValue,dMinOccurs,dMaxOccurs,
                                    (XSElementDecl)bParticle.fValue,bMinOccurs,bMaxOccurs);
                 return;
              }

              // Elt:Any NSCompat
              case XSParticleDecl.PARTICLE_WILDCARD:
              {
                 checkNSCompat((XSElementDecl)dParticle.fValue,dMinOccurs,dMaxOccurs,
                               (XSWildcardDecl)bParticle.fValue,bMinOccurs,bMaxOccurs);
                 return;
              }

              // Elt:All RecurseAsIfGroup
              case XSParticleDecl.PARTICLE_CHOICE:
              {
                 // Treat the element as if it were in a group of the same type
                 // as the base Particle
                 dChildren = new Vector();
                 dChildren.addElement(dParticle);

                 checkRecurseLax(dChildren, dMinOccurs, dMaxOccurs, dSGHandler,
                                 bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }
              case XSParticleDecl.PARTICLE_SEQUENCE:
              case XSParticleDecl.PARTICLE_ALL:
              {
                 // Treat the element as if it were in a group of the same type
                 // as the base Particle
                 dChildren = new Vector();
                 dChildren.addElement(dParticle);

                 checkRecurse(dChildren, dMinOccurs, dMaxOccurs, dSGHandler,
                              bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }

              default:
              {
                throw new XMLSchemaException("Internal-Error",
                                             new Object[]{"in particleValidRestriction"});
              }
            }
         }

         case XSParticleDecl.PARTICLE_WILDCARD:
         {
            switch (bType) {

              // Any:Any NSSubset
              case XSParticleDecl.PARTICLE_WILDCARD:
              {
                 checkNSSubset((XSWildcardDecl)dParticle.fValue, dMinOccurs, dMaxOccurs,
                               (XSWildcardDecl)bParticle.fValue, bMinOccurs, bMaxOccurs);
                 return;
              }

              case XSParticleDecl.PARTICLE_CHOICE:
              case XSParticleDecl.PARTICLE_SEQUENCE:
              case XSParticleDecl.PARTICLE_ALL:
              case XSParticleDecl.PARTICLE_ELEMENT:
              {
                 throw new XMLSchemaException("cos-particle-restrict.2",
                                        new Object[]{"any:choice,sequence,all,elt"});
              }

              default:
              {
                throw new XMLSchemaException("Internal-Error",
                                             new Object[]{"in particleValidRestriction"});
              }
            }
         }

         case XSParticleDecl.PARTICLE_ALL:
         {
            switch (bType) {

              // All:Any NSRecurseCheckCardinality
              case XSParticleDecl.PARTICLE_WILDCARD:
              {
                 if (dMinEffectiveTotalRange != OCCURRENCE_UNKNOWN)
                    dMinEffectiveTotalRange = dParticle.minEffectiveTotalRange();
                 if (dMaxEffectiveTotalRange != OCCURRENCE_UNKNOWN)
                    dMaxEffectiveTotalRange = dParticle.maxEffectiveTotalRange();

                 checkNSRecurseCheckCardinality(dChildren, dMinEffectiveTotalRange,
                                                dMaxEffectiveTotalRange,
                                                dSGHandler,
                                                bParticle,bMinOccurs,bMaxOccurs);

                 return;
              }

              case XSParticleDecl.PARTICLE_ALL:
              {
                 checkRecurse(dChildren, dMinOccurs, dMaxOccurs, dSGHandler,
                              bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }

              case XSParticleDecl.PARTICLE_CHOICE:
              case XSParticleDecl.PARTICLE_SEQUENCE:
              case XSParticleDecl.PARTICLE_ELEMENT:
              {
                 throw new XMLSchemaException("cos-particle-restrict.2",
                                        new Object[]{"all:choice,sequence,elt"});
              }

              default:
              {
                throw new XMLSchemaException("Internal-Error",
                                             new Object[]{"in particleValidRestriction"});
              }
            }
         }

         case XSParticleDecl.PARTICLE_CHOICE:
         {
            switch (bType) {

              // Choice:Any NSRecurseCheckCardinality
              case XSParticleDecl.PARTICLE_WILDCARD:
              {
                 if (dMinEffectiveTotalRange != OCCURRENCE_UNKNOWN)
                    dMinEffectiveTotalRange = dParticle.minEffectiveTotalRange();
                 if (dMaxEffectiveTotalRange != OCCURRENCE_UNKNOWN)
                    dMaxEffectiveTotalRange = dParticle.maxEffectiveTotalRange();

                 checkNSRecurseCheckCardinality(dChildren, dMinEffectiveTotalRange,
                                                dMaxEffectiveTotalRange,
                                                dSGHandler,
                                                bParticle,bMinOccurs,bMaxOccurs);
                 return;
              }

              case XSParticleDecl.PARTICLE_CHOICE:
              {
                 checkRecurseLax(dChildren, dMinOccurs, dMaxOccurs, dSGHandler,
                                 bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }

              case XSParticleDecl.PARTICLE_ALL:
              case XSParticleDecl.PARTICLE_SEQUENCE:
              case XSParticleDecl.PARTICLE_ELEMENT:
              {
                 throw new XMLSchemaException("cos-particle-restrict.2",
                                        new Object[]{"choice:all,sequence,elt"});
              }

              default:
              {
                throw new XMLSchemaException("Internal-Error",
                                             new Object[]{"in particleValidRestriction"});
              }
            }
         }


         case XSParticleDecl.PARTICLE_SEQUENCE:
         {
            switch (bType) {

              // Choice:Any NSRecurseCheckCardinality
              case XSParticleDecl.PARTICLE_WILDCARD:
              {
                 if (dMinEffectiveTotalRange != OCCURRENCE_UNKNOWN)
                    dMinEffectiveTotalRange = dParticle.minEffectiveTotalRange();
                 if (dMaxEffectiveTotalRange != OCCURRENCE_UNKNOWN)
                    dMaxEffectiveTotalRange = dParticle.maxEffectiveTotalRange();

                 checkNSRecurseCheckCardinality(dChildren, dMinEffectiveTotalRange,
                                                dMaxEffectiveTotalRange,
                                                dSGHandler,
                                                bParticle,bMinOccurs,bMaxOccurs);
                 return;
              }

              case XSParticleDecl.PARTICLE_ALL:
              {
                 checkRecurseUnordered(dChildren, dMinOccurs, dMaxOccurs, dSGHandler,
                                       bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }

              case XSParticleDecl.PARTICLE_SEQUENCE:
              {
                 checkRecurse(dChildren, dMinOccurs, dMaxOccurs, dSGHandler,
                              bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }

              case XSParticleDecl.PARTICLE_CHOICE:
              {
                 int min1 = dMinOccurs * dChildren.size();
                 int max1 = (dMaxOccurs == SchemaSymbols.OCCURRENCE_UNBOUNDED)?
                             dMaxOccurs : dMaxOccurs * dChildren.size();
                 checkMapAndSum(dChildren, min1, max1, dSGHandler,
                                bChildren, bMinOccurs, bMaxOccurs, bSGHandler);
                 return;
              }

              case XSParticleDecl.PARTICLE_ELEMENT:
              {
                 throw new XMLSchemaException("cos-particle-restrict.2",
                                        new Object[]{"seq:elt"});
              }

              default:
              {
                throw new XMLSchemaException("Internal-Error",
                                             new Object[]{"in particleValidRestriction"});
              }
            }
         }

       }
    }

    private static void addElementToParticleVector (Vector v, XSElementDecl d)  {

       XSParticleDecl p = new XSParticleDecl();
       p.fValue = d;
       p.fType = XSParticleDecl.PARTICLE_ELEMENT;
       v.addElement(p);

    }

    private static XSParticleDecl getNonUnaryGroup(XSParticleDecl p) {

       if (p.fType == XSParticleDecl.PARTICLE_ELEMENT ||
           p.fType == XSParticleDecl.PARTICLE_WILDCARD)
         return p;

       if (p.fMinOccurs==1 && p.fMaxOccurs==1 &&
           p.fValue!=null && p.fOtherValue==null)
         return getNonUnaryGroup((XSParticleDecl)p.fValue);
       else
         return p;
    }

    private static Vector removePointlessChildren(XSParticleDecl p)  {


       if (p.fType == XSParticleDecl.PARTICLE_ELEMENT ||
           p.fType == XSParticleDecl.PARTICLE_WILDCARD ||
           p.fType == XSParticleDecl.PARTICLE_EMPTY)
         return null;

       Vector children = new Vector();

       gatherChildren(p.fType, (XSParticleDecl)p.fValue, children);
       if (p.fOtherValue != null) {
         gatherChildren(p.fType, (XSParticleDecl)p.fOtherValue, children);
       }

       return children;
    }


    private static void gatherChildren(int parentType, XSParticleDecl p, Vector children) {

       int min = p.fMinOccurs;
       int max = p.fMaxOccurs;
       int type = p.fType;

       if (type == XSParticleDecl.PARTICLE_EMPTY)
          return;

       if (type == XSParticleDecl.PARTICLE_ELEMENT ||
           type== XSParticleDecl.PARTICLE_WILDCARD) {
          children.addElement(p);
          return;
       }

       XSParticleDecl left = (XSParticleDecl)p.fValue;
       XSParticleDecl right = (XSParticleDecl)p.fOtherValue;
       if (! (min==1 && max==1)) {
          children.addElement(p);
       }
       else if (parentType == type) {
          gatherChildren(type,left,children);
          if (right != null) {
            gatherChildren(type,right,children);
          }
       }
       else if (!p.isEmpty()) {
          children.addElement(p);
       }

    }

    private static void checkNameAndTypeOK(XSElementDecl dElement, int dMin, int dMax,
                                           XSElementDecl bElement, int bMin, int bMax)
                                   throws XMLSchemaException {


      //
      // Check that the names are the same
      //
      if (dElement.fName != bElement.fName ||
          dElement.fTargetNamespace != bElement.fTargetNamespace) {
          throw new XMLSchemaException(
              "rcase-NameAndTypeOK.1",new Object[]{dElement.fName,
               dElement.fTargetNamespace, bElement.fName, bElement.fTargetNamespace});
      }

      //
      // Check nillable
      //
      if (! (bElement.isNillable() || !dElement.isNillable())) {
        throw new XMLSchemaException("rcase-NameAndTypeOK.2",
                                      new Object[]{dElement.fName});
      }

      //
      // Check occurrence range
      //
      if (!checkOccurrenceRange(dMin, dMax, bMin, bMax)) {
        throw new XMLSchemaException("rcase-NameAndTypeOK.3",
                                      new Object[]{dElement.fName});
      }

      //
      // Check for consistent fixed values
      //
      if (bElement.getConstraintType() == XSElementDecl.FIXED_VALUE) {
         // derived one has to have a fixed value
         if (dElement.getConstraintType() != XSElementDecl.FIXED_VALUE) {
            throw new XMLSchemaException("rcase-NameAndTypeOK.4",
                                      new Object[]{dElement.fName});
         }

         // get simple type
         XSSimpleType dv = null;
         if (dElement.fType.getXSType() == XSTypeDecl.SIMPLE_TYPE)
            dv = (XSSimpleType)dElement.fType;
         else if (((XSComplexTypeDecl)dElement.fType).fContentType == XSComplexTypeDecl.CONTENTTYPE_SIMPLE)
            dv = ((XSComplexTypeDecl)dElement.fType).fXSSimpleType;

         // if there is no simple type, then compare based on string
         if (dv == null && !bElement.fDefault.normalizedValue.equals(dElement.fDefault.normalizedValue) ||
             dv != null && !dv.isEqual(bElement.fDefault.actualValue, dElement.fDefault.actualValue)) {
            throw new XMLSchemaException("rcase-NameAndTypeOK.4",
                                      new Object[]{dElement.fName});
         }
      }

      //
      // Check identity constraints
      //
      checkIDConstraintRestriction(dElement, bElement);

      //
      // Check for disallowed substitutions
      //
      int blockSet1 = dElement.fBlock;
      int blockSet2 = bElement.fBlock;
      if (((blockSet1 & blockSet2)!=blockSet2) ||
            (blockSet1==SchemaSymbols.EMPTY_SET && blockSet2!=SchemaSymbols.EMPTY_SET))
        throw new XMLSchemaException("rcase-NameAndTypeOK.6",
                                  new Object[]{dElement.fName});


      //
      // Check that the derived element's type is derived from the base's.
      //
      if (!checkTypeDerivationOk(dElement.fType, bElement.fType,
                                 (short)(SchemaSymbols.EXTENSION|SchemaSymbols.LIST|SchemaSymbols.UNION))) {
          throw new XMLSchemaException("rcase-NameAndTypeOK.7",
                                  new Object[]{dElement.fName});
      }

    }


    private static void checkIDConstraintRestriction(XSElementDecl derivedElemDecl,
                                                     XSElementDecl baseElemDecl)
                                             throws XMLSchemaException {
        // TODO
    } // checkIDConstraintRestriction


    private static boolean checkOccurrenceRange(int min1, int max1, int min2, int max2) {

      if ((min1 >= min2) &&
          ((max2==SchemaSymbols.OCCURRENCE_UNBOUNDED) ||
           (max1!=SchemaSymbols.OCCURRENCE_UNBOUNDED && max1<=max2)))
        return true;
      else
        return false;
    }

    private static void checkNSCompat(XSElementDecl elem, int min1, int max1,
                                      XSWildcardDecl wildcard, int min2, int max2)
                              throws XMLSchemaException {

      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
        throw new XMLSchemaException("rcase-NSCompat.2",
                                  new Object[]{elem.fName});
      }

      // check wildcard allows namespace of element
      if (!wildcard.allowNamespace(elem.fTargetNamespace))  {
        throw new XMLSchemaException("rcase-NSCompat.1",
                                  new Object[]{elem.fName,elem.fTargetNamespace});
      }

    }

    private static void checkNSSubset(XSWildcardDecl dWildcard, int min1, int max1,
                                      XSWildcardDecl bWildcard, int min2, int max2)
                              throws XMLSchemaException {

      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
        throw new XMLSchemaException("rcase-NSSubset.2",null);
      }

      // check wildcard subset
      if (!dWildcard.isSubsetOf(bWildcard)) {
         throw new XMLSchemaException("rcase-NSSubset.1",null);
      }


    }


    private static void checkNSRecurseCheckCardinality(Vector children, int min1, int max1,
                                          SubstitutionGroupHandler dSGHandler,
                                          XSParticleDecl wildcard, int min2, int max2)
                                          throws XMLSchemaException {


      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
         throw new XMLSchemaException("rcase-NSRecurseCheckCardinality.2", null);
      }

      // Check that each member of the group is a valid restriction of the wildcard
      int count = children.size();
      try {
        for (int i = 0; i < count; i++) {
           XSParticleDecl particle1 = (XSParticleDecl)children.elementAt(i);
           particleValidRestriction(particle1, dSGHandler, wildcard, null);

        }
      }
      catch (XMLSchemaException e) {
         throw new XMLSchemaException("rcase-NSRecurseCheckCardinality.1", null);
      }

    }

    private static void checkRecurse(Vector dChildren, int min1, int max1,
                                     SubstitutionGroupHandler dSGHandler,
                                     Vector bChildren, int min2, int max2,
                                     SubstitutionGroupHandler bSGHandler)
                                     throws XMLSchemaException {

      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
        throw new XMLSchemaException("rcase-Recurse.1", null);
      }

      int count1= dChildren.size();
      int count2= bChildren.size();

      int current = 0;
      label: for (int i = 0; i<count1; i++) {

        XSParticleDecl particle1 = (XSParticleDecl)dChildren.elementAt(i);
        for (int j = current; j<count2; j++) {
           XSParticleDecl particle2 = (XSParticleDecl)bChildren.elementAt(j);
           current +=1;
           try {
             particleValidRestriction(particle1, dSGHandler, particle2, bSGHandler);
             continue label;
           }
           catch (XMLSchemaException e) {
             if (!particle2.emptiable())
                throw new XMLSchemaException("rcase-Recurse.2", null);
           }
        }
        throw new XMLSchemaException("rcase-Recurse.2", null);
      }

      // Now, see if there are some elements in the base we didn't match up
      for (int j=current; j < count2; j++) {
        XSParticleDecl particle2 = (XSParticleDecl)bChildren.elementAt(j);
        if (!particle2.emptiable()) {
          throw new XMLSchemaException("rcase-Recurse.2", null);
        }
      }

    }

    private static void checkRecurseUnordered(Vector dChildren, int min1, int max1,
                                       SubstitutionGroupHandler dSGHandler,
                                       Vector bChildren, int min2, int max2,
                                       SubstitutionGroupHandler bSGHandler)
                                       throws XMLSchemaException {


      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
        throw new XMLSchemaException("rcase-RecurseUnordered.1", null);
      }

      int count1= dChildren.size();
      int count2 = bChildren.size();

      boolean foundIt[] = new boolean[count2];

      label: for (int i = 0; i<count1; i++) {
        XSParticleDecl particle1 = (XSParticleDecl)dChildren.elementAt(i);

        for (int j = 0; j<count2; j++) {
           XSParticleDecl particle2 = (XSParticleDecl)bChildren.elementAt(j);
           try {
             particleValidRestriction(particle1, dSGHandler, particle2, bSGHandler);
             if (foundIt[j])
                throw new XMLSchemaException("rcase-RecurseUnordered.2", null);
             else
                foundIt[j]=true;

             continue label;
           }
           catch (XMLSchemaException e) {
           }
        }
        // didn't find a match.  Detect an error
        throw new XMLSchemaException("rcase-RecurseUnordered.2", null);
      }

      // Now, see if there are some elements in the base we didn't match up
      for (int j=0; j < count2; j++) {
        XSParticleDecl particle2 = (XSParticleDecl)bChildren.elementAt(j);
        if (!foundIt[j] && !particle2.emptiable()) {
          throw new XMLSchemaException("rcase-RecurseUnordered.2", null);
        }
      }

    }

    private static void checkRecurseLax(Vector dChildren, int min1, int max1,
                                       SubstitutionGroupHandler dSGHandler,
                                       Vector bChildren, int min2, int max2,
                                       SubstitutionGroupHandler  bSGHandler)
                                       throws XMLSchemaException {

      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
        throw new XMLSchemaException("rcase-RecurseLax.1", null);
      }

      int count1= dChildren.size();
      int count2 = bChildren.size();

      int current = 0;
      label: for (int i = 0; i<count1; i++) {

        XSParticleDecl particle1 = (XSParticleDecl)dChildren.elementAt(i);
        for (int j = current; j<count2; j++) {
           XSParticleDecl particle2 = (XSParticleDecl)bChildren.elementAt(j);
           current +=1;
           try {
             particleValidRestriction(particle1, dSGHandler, particle2, bSGHandler);
             continue label;
           }
           catch (XMLSchemaException e) {
           }
        }
        // didn't find a match.  Detect an error
        throw new XMLSchemaException("rcase-RecurseLax.2", null);

      }

    }

    private static void checkMapAndSum(Vector dChildren, int min1, int max1,
                                       SubstitutionGroupHandler dSGHandler,
                                       Vector bChildren, int min2, int max2,
                                       SubstitutionGroupHandler bSGHandler)
                                       throws XMLSchemaException {

      // See if the sequence group is a valid restriction of the choice

      // Here is an example of a valid restriction:
      //   <choice minOccurs="2">
      //       <a/>
      //       <b/>
      //       <c/>
      //   </choice>
      //
      //   <sequence>
      //        <b/>
      //        <a/>
      //   </sequence>

      // check Occurrence ranges
      if (!checkOccurrenceRange(min1,max1,min2,max2)) {
        throw new XMLSchemaException("rcase-MapAndSum.2", null);
      }

      int count1 = dChildren.size();
      int count2 = bChildren.size();

      label: for (int i = 0; i<count1; i++) {

        XSParticleDecl particle1 = (XSParticleDecl)dChildren.elementAt(i);
        for (int j = 0; j<count2; j++) {
           XSParticleDecl particle2 = (XSParticleDecl)bChildren.elementAt(j);
           try {
             particleValidRestriction(particle1, dSGHandler, particle2, bSGHandler);
             continue label;
           }
           catch (XMLSchemaException e) {
           }
        }
        // didn't find a match.  Detect an error
        throw new XMLSchemaException("rcase-MapAndSum.1", null);
      }
    }
    // to check whether two element overlap, as defined in constraint UPA
    public static boolean overlapUPA(XSElementDecl element1,
                                     XSElementDecl element2,
                                     SubstitutionGroupHandler sgHandler) {
        // if the two element have the same name and namespace,
        if (element1.fName == element2.fName &&
            element1.fTargetNamespace == element2.fTargetNamespace) {
            return true;
        }

        // or if there is an element decl in element1's substitution group,
        // who has the same name/namespace with element2
        XSElementDecl[] subGroup = sgHandler.getSubstitutionGroup(element1);
        for (int i = subGroup.length-1; i >= 0; i--) {
            if (subGroup[i].fName == element2.fName &&
                subGroup[i].fTargetNamespace == element2.fTargetNamespace) {
                return true;
            }
        }

        // or if there is an element decl in element2's substitution group,
        // who has the same name/namespace with element1
        subGroup = sgHandler.getSubstitutionGroup(element2);
        for (int i = subGroup.length-1; i >= 0; i--) {
            if (subGroup[i].fName == element1.fName &&
                subGroup[i].fTargetNamespace == element1.fTargetNamespace) {
                return true;
            }
        }

        return false;
    }

    // to check whether an element overlaps with a wildcard,
    // as defined in constraint UPA
    public static boolean overlapUPA(XSElementDecl element,
                                     XSWildcardDecl wildcard,
                                     SubstitutionGroupHandler sgHandler) {
        // if the wildcard allows the element
        if (wildcard.allowNamespace(element.fTargetNamespace))
            return true;

        // or if the wildcard allows any element in the substitution group
        XSElementDecl[] subGroup = sgHandler.getSubstitutionGroup(element);
        for (int i = subGroup.length-1; i >= 0; i--) {
            if (wildcard.allowNamespace(subGroup[i].fTargetNamespace))
                return true;
        }

        return false;
    }

    public static boolean overlapUPA(XSWildcardDecl wildcard1,
                                     XSWildcardDecl wildcard2) {
        // if the intersection of the two wildcard is not empty list
        XSWildcardDecl intersect = wildcard1.performIntersectionWith(wildcard2, wildcard1.fProcessContents);
        if (intersect == null ||
            intersect.fType != XSWildcardDecl.WILDCARD_LIST ||
            intersect.fNamespaceList.length != 0) {
            return true;
        }

        return false;
    }

    // call one of the above methods according to the type of decls
    public static boolean overlapUPA(Object decl1, Object decl2,
                                     SubstitutionGroupHandler sgHandler) {
        if (decl1 instanceof XSElementDecl) {
            if (decl2 instanceof XSElementDecl) {
                return overlapUPA((XSElementDecl)decl1,
                                  (XSElementDecl)decl2,
                                  sgHandler);
            } else {
                return overlapUPA((XSElementDecl)decl1,
                                  (XSWildcardDecl)decl2,
                                  sgHandler);
            }
        } else {
            if (decl2 instanceof XSElementDecl) {
                return overlapUPA((XSElementDecl)decl2,
                                  (XSWildcardDecl)decl1,
                                  sgHandler);
            } else {
                return overlapUPA((XSWildcardDecl)decl1,
                                  (XSWildcardDecl)decl2);
            }
        }
    }

} // class XSContraints
